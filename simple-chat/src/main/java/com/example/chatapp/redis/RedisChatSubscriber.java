package com.example.chatapp.redis;

import com.example.chatapp.model.ChatMessage;
import com.example.chatapp.realtime.ChatEventHub;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class RedisChatSubscriber implements MessageListener {
    private static final Logger log =
            LoggerFactory.getLogger(RedisChatSubscriber.class);

    private static final String PREFIX = "chat:room:{";
    private static final String SUFFIX = "}:events";

    private final ChatEventHub eventHub;
    private final ObjectMapper objectMapper;

    public RedisChatSubscriber(ChatEventHub eventHub, ObjectMapper objectMapper) {
        this.eventHub = eventHub;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);

        if (!channel.startsWith(PREFIX) || !channel.endsWith(SUFFIX)) {
            return;
        }

        String roomId = channel.substring(
                PREFIX.length(),
                channel.length() - SUFFIX.length()
        );

        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            ChatMessage chatMessage = objectMapper.readValue(body, ChatMessage.class);
            eventHub.broadcast(roomId, chatMessage);
        } catch (JsonProcessingException e) {
            log.warn("Ignoring invalid chat event from Redis channel {}", channel, e);
        }
    }
}
