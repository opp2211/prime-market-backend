package ru.maltsev.primemarketbackend.notification.domain;

public final class NotificationTypes {
    public static final String ORDER_CREATED = "order_created";
    public static final String ORDER_STATUS_CHANGED = "order_status_changed";
    public static final String ORDER_MESSAGE_RECEIVED = "order_message_received";
    public static final String ORDER_REQUEST_CREATED = "order_request_created";
    public static final String ORDER_REQUEST_RESOLVED = "order_request_resolved";
    public static final String DISPUTE_OPENED = "dispute_opened";
    public static final String DISPUTE_TAKEN_IN_WORK = "dispute_taken_in_work";
    public static final String DEPOSIT_CONFIRMED = "deposit_confirmed";
    public static final String DEPOSIT_REJECTED = "deposit_rejected";
    public static final String WITHDRAWAL_COMPLETED = "withdrawal_completed";
    public static final String WITHDRAWAL_REJECTED = "withdrawal_rejected";

    private NotificationTypes() {
    }
}
