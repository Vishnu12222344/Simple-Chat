package com.example.chatapp.error;

public final class ChatExceptions {
    private ChatExceptions() {}

    public static class DuplicateRoom extends RuntimeException {
        public DuplicateRoom(String message) { super(message); }
    }

    public static class RoomNotFound extends RuntimeException {
        public RoomNotFound(String message) { super(message); }
    }

    public static class ParticipantNotJoined extends RuntimeException {
        public ParticipantNotJoined(String message) { super(message); }
    }

    public static class InvalidChatRequest extends RuntimeException {
        public InvalidChatRequest(String message) { super(message); }
    }
}
