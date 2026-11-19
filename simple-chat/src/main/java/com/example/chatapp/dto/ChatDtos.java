package com.example.chatapp.dto;

import com.example.chatapp.model.ChatMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ChatDtos {
    private ChatDtos() {}

    public record CreateRoomRequest(
            @NotBlank(message = "roomName is required")
            @Pattern(
                    regexp = "^[A-Za-z0-9_-]{1,64}$",
                    message = "roomName may contain only letters, numbers, '-' and '_'"
            )
            String roomName
    ) {}

    public record JoinRoomRequest(
            @NotBlank(message = "participant is required")
            @Size(max = 64)
            String participant
    ) {}

    public record SendMessageRequest(
            @NotBlank(message = "participant is required")
            @Size(max = 64)
            String participant,

            @NotBlank(message = "message is required")
            @Size(max = 2000)
            String message
    ) {}

    public record ApiResponse(String message, String status) {}

    public record CreateRoomResponse(String message, String roomId, String status) {}

    public record MessagesResponse(List<ChatMessage> messages) {}

    public record ApiError(String message, String status) {}
}
