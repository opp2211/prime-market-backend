package ru.maltsev.primemarketbackend.withdrawal.domain;

public enum WithdrawalRequestStatus {
    OPEN,
    PROCESSING,
    COMPLETED,
    CANCELLED,
    REJECTED
}
