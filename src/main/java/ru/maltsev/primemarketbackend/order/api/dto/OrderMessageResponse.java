package ru.maltsev.primemarketbackend.order.api.dto;

import java.time.Instant;

public record OrderMessageResponse(
    Long id,
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
