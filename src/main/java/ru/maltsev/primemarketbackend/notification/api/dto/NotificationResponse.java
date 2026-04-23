package ru.maltsev.primemarketbackend.notification.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import ru.maltsev.primemarketbackend.notification.domain.Notification;

public record NotificationResponse(
    UUID publicId,
    String type,
    String title,
    String body,
    JsonNode payload,
    boolean isRead,
    Instant createdAt,
    Instant readAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
            notification.getPublicId(),
            notification.getType(),
            notification.getTitle(),
            notification.getBody(),
            notification.getPayload(),
            notification.isRead(),
            notification.getCreatedAt(),
            notification.getReadAt()
        );
    }
}
