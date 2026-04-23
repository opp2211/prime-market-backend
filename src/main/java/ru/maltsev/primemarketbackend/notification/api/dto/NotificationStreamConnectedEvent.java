package ru.maltsev.primemarketbackend.notification.api.dto;

import java.time.Instant;

public record NotificationStreamConnectedEvent(
    String connectionId,
    Instant connectedAt
) {
}
