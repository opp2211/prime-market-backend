package ru.maltsev.primemarketbackend.order.repository;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletReserveReadRow(
    String sourceType,
    String refCode,
    Long refId,
    String title,
    String description,
    BigDecimal amount,
    String currencyCode,
    String status,
    Instant createdAt
) {
}
