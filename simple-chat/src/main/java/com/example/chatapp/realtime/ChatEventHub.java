package com.example.chatapp.realtime;

import com.example.chatapp.model.ChatMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ChatEventHub {
    private final ConcurrentMap<String, CopyOnWriteArrayList<SseEmitter>> clients =
            new ConcurrentHashMap<>();

    public SseEmitter subscribe(String roomId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        clients.computeIfAbsent(roomId, ignored -> new CopyOnWriteArrayList<>())
                .add(emitter);

        Runnable removeClient = () -> remove(roomId, emitter);
        emitter.onCompletion(removeClient);
        emitter.onTimeout(() -> {
            removeClient.run();
            emitter.complete();
        });
        emitter.onError(error -> removeClient.run());

        return emitter;
    }

    public void broadcast(String roomId, ChatMessage message) {
        var subscribers = clients.get(roomId);
        if (subscribers == null) {
            return;
        }

        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().name("message").data(message));
            } catch (IOException | IllegalStateException e) {
                remove(roomId, emitter);
                emitter.completeWithError(e);
            }
        }
    }

    private void remove(String roomId, SseEmitter emitter) {
        clients.computeIfPresent(roomId, (ignored, subscribers) -> {
            subscribers.remove(emitter);
            return subscribers.isEmpty() ? null : subscribers;
        });
    }
}
