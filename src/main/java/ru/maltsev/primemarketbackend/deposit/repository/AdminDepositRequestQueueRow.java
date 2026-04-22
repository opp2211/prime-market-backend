package ru.maltsev.primemarketbackend.deposit.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequestStatus;

public record AdminDepositRequestQueueRow(
    UUID publicId,
    BigDecimal amount,
    String currencyCode,
    Long depositMethodId,
    String depositMethodTitle,
    DepositRequestStatus status,
    Long userId,
    String username,
    Instant detailsIssuedAt,
    Instant userMarkedPaidAt,
    Instant confirmedAt,
    Instant rejectedAt,
    Instant cancelledAt,
    Instant createdAt,
    Instant updatedAt
) {
}
