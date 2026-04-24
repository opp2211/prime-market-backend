package ru.maltsev.primemarketbackend.notification.api.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import ru.maltsev.primemarketbackend.notification.domain.Notification;

public record NotificationResponse(
    UUID publicId,
    String type,
    String title,
    String body,
    Map<String, Object> payload,
    boolean isRead,
    Instant createdAt,
    Instant readAt
) {
    private static final JsonMapper JSON_MAPPER = new JsonMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
            notification.getPublicId(),
            notification.getType(),
            notification.getTitle(),
            notification.getBody(),
            normalizePayload(notification.getPayload()),
            notification.isRead(),
            notification.getCreatedAt(),
            notification.getReadAt()
        );
    }

    private static Map<String, Object> normalizePayload(JsonNode payload) {
        if (payload == null) {
            return Map.of();
        }

        try {
            return JSON_MAPPER.readValue(payload.toString(), PAYLOAD_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to normalize notification payload JSON", ex);
        }
    }
}
