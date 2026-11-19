package com.example.chatapp.api;

import com.example.chatapp.dto.ChatDtos.ApiResponse;
import com.example.chatapp.dto.ChatDtos.CreateRoomRequest;
import com.example.chatapp.dto.ChatDtos.CreateRoomResponse;
import com.example.chatapp.dto.ChatDtos.JoinRoomRequest;
import com.example.chatapp.dto.ChatDtos.MessagesResponse;
import com.example.chatapp.dto.ChatDtos.SendMessageRequest;
import com.example.chatapp.realtime.ChatEventHub;
import com.example.chatapp.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chatapp")
public class ChatController {
    private final ChatService chatService;
    private final ChatEventHub eventHub;

    public ChatController(ChatService chatService, ChatEventHub eventHub) {
        this.chatService = chatService;
        this.eventHub = eventHub;
    }

    @PostMapping("/chatrooms")
    public CreateRoomResponse createRoom(@Valid @RequestBody CreateRoomRequest request) {
        String roomId = chatService.createRoom(request.roomName());

        return new CreateRoomResponse(
                "Chat room '" + roomId + "' created successfully.",
                roomId,
                "success"
        );
    }

    @PostMapping("/chatrooms/{roomId}/join")
    public ApiResponse joinRoom(
            @PathVariable String roomId,
            @Valid @RequestBody JoinRoomRequest request
    ) {
        chatService.joinRoom(roomId, request.participant());

        return new ApiResponse(
                "User '" + request.participant() + "' joined chat room '" + roomId + "'.",
                "success"
        );
    }

    @PostMapping("/chatrooms/{roomId}/messages")
    public ApiResponse sendMessage(
            @PathVariable String roomId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        chatService.sendMessage(roomId, request.participant(), request.message());

        return new ApiResponse("Message sent successfully.", "success");
    }

    @GetMapping("/chatrooms/{roomId}/messages")
    public MessagesResponse getHistory(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return new MessagesResponse(chatService.getHistory(roomId, limit));
    }
    @GetMapping(value = "/chatrooms/{roomId}/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String roomId,
            @RequestParam String participant
    ) {
        chatService.assertParticipantJoined(roomId, participant);
        return eventHub.subscribe(roomId);
    }
}
