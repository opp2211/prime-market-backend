package ru.maltsev.primemarketbackend.account.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record WalletWorkItemResponse(
    @JsonProperty("source_type") String sourceType,
    @JsonProperty("ref_code") String refCode,
    @JsonProperty("ref_id") Long refId,
    String title,
    String description,
    BigDecimal amount,
    @JsonProperty("currency_code") String currencyCode,
    String status,
    @JsonProperty("created_at") Instant createdAt
) {
}
