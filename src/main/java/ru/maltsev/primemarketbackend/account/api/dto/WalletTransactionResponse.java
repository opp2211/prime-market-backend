package ru.maltsev.primemarketbackend.account.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletTransactionResponse(
    @JsonProperty("public_id") UUID publicId,
    BigDecimal amount,
    @JsonProperty("currency_code") String currencyCode,
    String type,
    String label,
    @JsonProperty("ref_type") String refType,
    @JsonProperty("ref_public_id") UUID refPublicId,
    @JsonProperty("created_at") Instant createdAt
) {
}
