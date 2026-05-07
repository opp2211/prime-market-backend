package ru.maltsev.primemarketbackend.account.repository;

import java.math.BigDecimal;
import java.time.Instant;

public record UserAccountTxReadRow(
    Long id,
    String publicCode,
    BigDecimal amount,
    String currencyCode,
    String txType,
    String refType,
    Long refId,
    Instant createdAt
) {
}
