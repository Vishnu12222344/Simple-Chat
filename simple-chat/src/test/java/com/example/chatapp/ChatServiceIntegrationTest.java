package com.example.chatapp;

import com.example.chatapp.error.ChatExceptions.DuplicateRoom;
import com.example.chatapp.error.ChatExceptions.ParticipantNotJoined;
import com.example.chatapp.error.ChatExceptions.RoomNotFound;
import com.example.chatapp.model.ChatMessage;
import com.example.chatapp.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class ChatServiceIntegrationTest {

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    ChatService chatService;

    private String uniqueRoom() {
        return "room-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void createAndJoinRoom() {
        String room = uniqueRoom();
        String roomId = chatService.createRoom(room);
        assertThat(roomId).isEqualTo(room);

        chatService.joinRoom(room, "guest_user");
        chatService.assertParticipantJoined(room, "guest_user");
    }

    @Test
    void duplicateRoomIsRejected() {
        String room = uniqueRoom();
        chatService.createRoom(room);

        assertThatThrownBy(() -> chatService.createRoom(room))
                .isInstanceOf(DuplicateRoom.class);
    }

    @Test
    void sendAndRetrieveMessagesInOrder() {
        String room = uniqueRoom();
        chatService.createRoom(room);
        chatService.joinRoom(room, "guest_user");
        chatService.sendMessage(room, "guest_user", "first");
        chatService.sendMessage(room, "guest_user", "second");

        List<ChatMessage> history = chatService.getHistory(room, 10);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).message()).isEqualTo("first");
        assertThat(history.get(1).message()).isEqualTo("second");

        List<ChatMessage> last = chatService.getHistory(room, 1);
        assertThat(last).hasSize(1);
        assertThat(last.get(0).message()).isEqualTo("second");
    }

    @Test
    void sendingToMissingRoomFails() {
        assertThatThrownBy(() ->
                chatService.sendMessage(uniqueRoom(), "guest_user", "hi"))
                .isInstanceOf(RoomNotFound.class);
    }

    @Test
    void sendingWithoutJoiningFails() {
        String room = uniqueRoom();
        chatService.createRoom(room);

        assertThatThrownBy(() ->
                chatService.sendMessage(room, "ghost", "hi"))
                .isInstanceOf(ParticipantNotJoined.class);
    }
}
