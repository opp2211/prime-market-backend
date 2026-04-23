package ru.maltsev.primemarketbackend.notification.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notifications.sse")
public record NotificationStreamProperties(
    Duration emitterTimeout,
    Duration keepaliveInterval
) {
    public NotificationStreamProperties {
        emitterTimeout = emitterTimeout == null ? Duration.ofMinutes(30) : emitterTimeout;
        keepaliveInterval = keepaliveInterval == null ? Duration.ofSeconds(25) : keepaliveInterval;
    }
}
