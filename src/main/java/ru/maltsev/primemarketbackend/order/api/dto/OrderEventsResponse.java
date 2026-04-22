package ru.maltsev.primemarketbackend.order.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public record OrderEventsResponse(List<Item> items) {
    public record Item(
        Long id,
        String eventType,
        Actor actor,
        JsonNode payload,
        Instant createdAt
    ) {
    }

    public record Actor(
        Long userId,
        String role
    ) {
    }
}
