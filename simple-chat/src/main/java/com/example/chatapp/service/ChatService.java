package com.example.chatapp.service;

import com.example.chatapp.model.ChatMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import static com.example.chatapp.error.ChatExceptions.*;

@Service
public class ChatService {
    private static final Pattern ROOM_NAME =
            Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private static final int MAX_HISTORY_LIMIT = 100;

    // Atomic: create room metadata hash only if it does not exist.
    private static final DefaultRedisScript<Long> CREATE_ROOM_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('EXISTS', KEYS[1]) == 1 then
                        return 0
                    end
                    redis.call('HSET', KEYS[1],
                        'roomName', ARGV[1],
                        'createdAt', ARGV[2])
                    return 1
                    """,
                    Long.class
            );

    // Atomic: validate room + membership, store message, publish event.
    private static final DefaultRedisScript<Long> SEND_MESSAGE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('EXISTS', KEYS[1]) == 0 then
                        return -1
                    end
                    if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 0 then
                        return -2
                    end
                    redis.call('RPUSH', KEYS[3], ARGV[2])
                    redis.call('PUBLISH', KEYS[4], ARGV[2])
                    return 1
                    """,
                    Long.class
            );

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ChatService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public String createRoom(String roomName) {
        validateRoomName(roomName);

        Long result = redis.execute(
                CREATE_ROOM_SCRIPT,
                List.of(metadataKey(roomName)),
                roomName,
                Instant.now().toString()
        );

        if (result == null) {
            throw new IllegalStateException("Redis did not return a room creation result.");
        }
        if (result == 0L) {
            throw new DuplicateRoom("Chat room '" + roomName + "' already exists.");
        }

        return roomName;
    }

    public void joinRoom(String roomId, String participant) {
        validateRoomName(roomId);
        assertRoomExists(roomId);
        redis.opsForSet().add(participantsKey(roomId), participant);
        // SADD makes joining the same room twice harmless.
    }

    public void assertParticipantJoined(String roomId, String participant) {
        assertRoomExists(roomId);

        Boolean member = redis.opsForSet()
                .isMember(participantsKey(roomId), participant);

        if (!Boolean.TRUE.equals(member)) {
            throw new ParticipantNotJoined(
                    "User '" + participant + "' has not joined chat room '" + roomId + "'."
            );
        }
    }

    public void sendMessage(String roomId, String participant, String text) {
        validateRoomName(roomId);

        ChatMessage chatMessage = new ChatMessage(participant, text, Instant.now());

        Long result = redis.execute(
                SEND_MESSAGE_SCRIPT,
                List.of(
                        metadataKey(roomId),
                        participantsKey(roomId),
                        messagesKey(roomId),
                        eventsChannel(roomId)
                ),
                participant,
                toJson(chatMessage)
        );

        if (result == null) {
            throw new IllegalStateException("Redis did not return a message result.");
        }
        if (result == -1L) {
            throw new RoomNotFound("Chat room '" + roomId + "' does not exist.");
        }
        if (result == -2L) {
            throw new ParticipantNotJoined(
                    "User '" + participant + "' must join chat room '" + roomId + "' before sending."
            );
        }
        if (result != 1L) {
            throw new IllegalStateException("Unexpected Redis result: " + result);
        }
    }

    public List<ChatMessage> getHistory(String roomId, int limit) {
        validateRoomName(roomId);

        if (limit < 1 || limit > MAX_HISTORY_LIMIT) {
            throw new InvalidChatRequest(
                    "limit must be between 1 and " + MAX_HISTORY_LIMIT + "."
            );
        }

        assertRoomExists(roomId);

        List<String> stored = redis.opsForList()
                .range(messagesKey(roomId), -((long) limit), -1L);

        if (stored == null) {
            return List.of();
        }

        return stored.stream().map(this::fromJson).toList();
    }

    public void assertRoomExists(String roomId) {
        validateRoomName(roomId);

        if (!Boolean.TRUE.equals(redis.hasKey(metadataKey(roomId)))) {
            throw new RoomNotFound("Chat room '" + roomId + "' does not exist.");
        }
    }

    private void validateRoomName(String roomId) {
        if (roomId == null || !ROOM_NAME.matcher(roomId).matches()) {
            throw new InvalidChatRequest(
                    "Room name must be 1-64 characters and use only letters, numbers, '-' or '_'."
            );
        }
    }

    private String toJson(ChatMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize chat message.", e);
        }
    }

    private ChatMessage fromJson(String json) {
        try {
            return objectMapper.readValue(json, ChatMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read a stored chat message.", e);
        }
    }

    private String metadataKey(String roomId)    { return key(roomId, "meta"); }
    private String participantsKey(String roomId){ return key(roomId, "participants"); }
    private String messagesKey(String roomId)    { return key(roomId, "messages"); }
    private String eventsChannel(String roomId)  { return key(roomId, "events"); }

    private String key(String roomId, String suffix) {
        return "chat:room:{" + roomId + "}:" + suffix;
    }
}
