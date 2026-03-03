package ru.maltsev.primemarketbackend.account.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UserAccountTxShortResponse(
        @JsonProperty("public_id") UUID publicId,
        BigDecimal amount,
        @JsonProperty("currency_code") String currencyCode,
        String type,
        @JsonProperty("created_at") Instant createdAt
) {
}
