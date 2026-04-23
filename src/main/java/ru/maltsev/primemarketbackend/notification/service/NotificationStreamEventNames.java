package ru.maltsev.primemarketbackend.notification.service;

public final class NotificationStreamEventNames {
    public static final String STREAM_CONNECTED = "stream.connected";
    public static final String STREAM_KEEPALIVE = "stream.keepalive";
    public static final String NOTIFICATION_CREATED = "notification.created";
    public static final String UNREAD_COUNT_UPDATED = "notifications.unread_count";

    private NotificationStreamEventNames() {
    }
}
