package ru.maltsev.primemarketbackend.order.api.dto;

import java.time.Instant;

public record OrderConversationResponse(
    Long id,
    String conversationType,
    String title,
    Instant lastMessageAt,
    boolean hasMessages
) {
}
