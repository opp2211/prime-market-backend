package ru.maltsev.primemarketbackend.notification.service;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@RequiredArgsConstructor
public class NotificationSseRegistry {
    private final NotificationStreamProperties properties;
    private final Map<Long, Map<String, SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

    public NotificationSseSession register(Long userId) {
        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = new ManagedSseEmitter(properties.emitterTimeout().toMillis(), () -> remove(userId, connectionId));
        Map<String, SseEmitter> userEmitters = emittersByUserId.computeIfAbsent(
            userId,
            ignored -> new ConcurrentHashMap<>()
        );
        userEmitters.put(connectionId, emitter);
        emitter.onCompletion(() -> remove(userId, connectionId));
        emitter.onTimeout(() -> {
            remove(userId, connectionId);
            emitter.complete();
        });
        emitter.onError(error -> remove(userId, connectionId));
        return new DefaultNotificationSseSession(connectionId, emitter);
    }

    public boolean sendToConnection(Long userId, String connectionId, String eventName, Object payload) {
        Map<String, SseEmitter> userEmitters = emittersByUserId.get(userId);
        if (userEmitters == null) {
            return false;
        }

        SseEmitter emitter = userEmitters.get(connectionId);
        if (emitter == null) {
            return false;
        }

        return send(userId, connectionId, emitter, eventName, payload);
    }

    public void sendToUser(Long userId, String eventName, Object payload) {
        Map<String, SseEmitter> userEmitters = emittersByUserId.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        for (Map.Entry<String, SseEmitter> entry : userEmitters.entrySet()) {
            send(userId, entry.getKey(), entry.getValue(), eventName, payload);
        }
    }

    public void sendToAll(String eventName, Object payload) {
        for (Map.Entry<Long, Map<String, SseEmitter>> entry : emittersByUserId.entrySet()) {
            for (Map.Entry<String, SseEmitter> emitterEntry : entry.getValue().entrySet()) {
                send(entry.getKey(), emitterEntry.getKey(), emitterEntry.getValue(), eventName, payload);
            }
        }
    }

    public void disconnectAll() {
        emittersByUserId.values().forEach(userEmitters -> userEmitters.values().forEach(SseEmitter::complete));
        emittersByUserId.clear();
    }

    public int connectionCount(Long userId) {
        Map<String, SseEmitter> userEmitters = emittersByUserId.get(userId);
        return userEmitters == null ? 0 : userEmitters.size();
    }

    public int totalConnectionCount() {
        return emittersByUserId.values().stream()
            .mapToInt(Map::size)
            .sum();
    }

    private boolean send(Long userId, String connectionId, SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload, MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException | IllegalStateException ex) {
            remove(userId, connectionId);
            emitter.completeWithError(ex);
            return false;
        }
    }

    private void remove(Long userId, String connectionId) {
        emittersByUserId.computeIfPresent(userId, (ignored, userEmitters) -> {
            userEmitters.remove(connectionId);
            return userEmitters.isEmpty() ? null : userEmitters;
        });
    }

    public interface NotificationSseSession {
        String connectionId();

        SseEmitter emitter();
    }

    private static final class ManagedSseEmitter extends SseEmitter {
        private final Runnable cleanup;

        private ManagedSseEmitter(Long timeout, Runnable cleanup) {
            super(timeout);
            this.cleanup = cleanup;
        }

        @Override
        public synchronized void complete() {
            cleanup.run();
            super.complete();
        }

        @Override
        public synchronized void completeWithError(Throwable ex) {
            cleanup.run();
            super.completeWithError(ex);
        }
    }

    private record DefaultNotificationSseSession(String connectionId, SseEmitter emitter) implements NotificationSseSession {
    }
}
