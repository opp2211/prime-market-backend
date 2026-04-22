package ru.maltsev.primemarketbackend.account.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UserAccountTxReadRow(
    Long id,
    UUID publicId,
    BigDecimal amount,
    String currencyCode,
    String txType,
    String refType,
    Long refId,
    Instant createdAt
) {
}
