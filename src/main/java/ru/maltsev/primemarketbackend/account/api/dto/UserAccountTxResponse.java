package ru.maltsev.primemarketbackend.account.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UserAccountTxResponse(
        Instant dateTime,
        String type,
        BigDecimal amount,
        String currency,
        UUID publicId
) {
}
