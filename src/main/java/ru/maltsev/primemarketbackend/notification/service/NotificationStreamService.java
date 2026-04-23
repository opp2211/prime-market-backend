package ru.maltsev.primemarketbackend.notification.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.maltsev.primemarketbackend.notification.api.dto.NotificationResponse;
import ru.maltsev.primemarketbackend.notification.api.dto.NotificationStreamConnectedEvent;
import ru.maltsev.primemarketbackend.notification.api.dto.NotificationStreamKeepaliveEvent;
import ru.maltsev.primemarketbackend.notification.api.dto.UnreadNotificationsCountResponse;
import ru.maltsev.primemarketbackend.notification.repository.NotificationRepository;

@Service
@RequiredArgsConstructor
public class NotificationStreamService {
    private final NotificationRepository notificationRepository;
    private final NotificationSseRegistry notificationSseRegistry;

    public SseEmitter subscribe(Long userId) {
        NotificationSseRegistry.NotificationSseSession session = notificationSseRegistry.register(userId);
        notificationSseRegistry.sendToConnection(
            userId,
            session.connectionId(),
            NotificationStreamEventNames.STREAM_CONNECTED,
            new NotificationStreamConnectedEvent(session.connectionId(), Instant.now())
        );
        notificationSseRegistry.sendToConnection(
            userId,
            session.connectionId(),
            NotificationStreamEventNames.UNREAD_COUNT_UPDATED,
            unreadCountPayload(userId)
        );
        return session.emitter();
    }

    public void publishNotificationCreated(Long userId, UUID publicId) {
        notificationRepository.findByPublicIdAndUserId(publicId, userId)
            .map(NotificationResponse::from)
            .ifPresent(payload -> notificationSseRegistry.sendToUser(
                userId,
                NotificationStreamEventNames.NOTIFICATION_CREATED,
                payload
            ));
    }

    public void publishUnreadCount(Long userId) {
        notificationSseRegistry.sendToUser(
            userId,
            NotificationStreamEventNames.UNREAD_COUNT_UPDATED,
            unreadCountPayload(userId)
        );
    }

    @Scheduled(fixedDelayString = "${app.notifications.sse.keepalive-interval:25s}")
    public void publishKeepalive() {
        if (notificationSseRegistry.totalConnectionCount() == 0) {
            return;
        }

        notificationSseRegistry.sendToAll(
            NotificationStreamEventNames.STREAM_KEEPALIVE,
            new NotificationStreamKeepaliveEvent(Instant.now())
        );
    }

    public void disconnectAll() {
        notificationSseRegistry.disconnectAll();
    }

    private UnreadNotificationsCountResponse unreadCountPayload(Long userId) {
        return new UnreadNotificationsCountResponse(notificationRepository.countByUserIdAndIsReadFalse(userId));
    }
}
