package ru.maltsev.primemarketbackend.deposit.domain;

public enum DepositRequestStatus {
    PENDING_DETAILS,
    WAITING_PAYMENT,
    PAYMENT_VERIFICATION,
    CONFIRMED,
    REJECTED,
    EXPIRED,
    CANCELLED
}
