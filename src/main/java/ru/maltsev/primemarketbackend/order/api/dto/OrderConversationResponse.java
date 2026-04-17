package ru.maltsev.primemarketbackend.order.api.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderConversationResponse(
    UUID publicId,
    String conversationType,
    String title,
    Instant lastMessageAt,
    boolean hasMessages
) {
}
