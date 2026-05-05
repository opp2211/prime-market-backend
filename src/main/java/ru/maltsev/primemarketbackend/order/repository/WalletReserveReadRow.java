package ru.maltsev.primemarketbackend.order.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletReserveReadRow(
    String sourceType,
    UUID refPublicId,
    Long refId,
    String title,
    String description,
    BigDecimal amount,
    String currencyCode,
    String status,
    Instant createdAt
) {
}
