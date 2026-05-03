package ru.maltsev.primemarketbackend.treasury.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record TreasuryExposureRowResponse(
    @JsonProperty("currency_code") String currencyCode,
    @JsonProperty("treasury_balance") BigDecimal treasuryBalance,
    @JsonProperty("user_balance") BigDecimal userBalance,
    @JsonProperty("user_reserved") BigDecimal userReserved,
    @JsonProperty("user_available") BigDecimal userAvailable,
    @JsonProperty("platform_balance") BigDecimal platformBalance,
    @JsonProperty("expected_treasury_balance") BigDecimal expectedTreasuryBalance,
    @JsonProperty("difference") BigDecimal difference
) {
}
