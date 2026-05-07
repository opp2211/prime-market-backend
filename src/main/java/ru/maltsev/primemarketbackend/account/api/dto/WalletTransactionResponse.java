package ru.maltsev.primemarketbackend.account.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record WalletTransactionResponse(
    @JsonProperty("public_code") String publicCode,
    BigDecimal amount,
    @JsonProperty("currency_code") String currencyCode,
    String type,
    String label,
    @JsonProperty("ref_type") String refType,
    @JsonProperty("ref_code") String refCode,
    @JsonProperty("created_at") Instant createdAt
) {
}
