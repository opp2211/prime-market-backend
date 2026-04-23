package ru.maltsev.primemarketbackend.notification.api.dto;

import java.time.Instant;

public record NotificationStreamKeepaliveEvent(Instant timestamp) {
}
