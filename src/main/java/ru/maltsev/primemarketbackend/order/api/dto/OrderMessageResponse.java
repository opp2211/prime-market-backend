package ru.maltsev.primemarketbackend.order.api.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderMessageResponse(
    UUID publicId,
    String messageType,
    String body,
    Sender sender,
    Instant createdAt
) {
    public record Sender(
        Long userId,
        String role,
        String username
    ) {
    }
}
