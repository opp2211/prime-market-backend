package ru.maltsev.primemarketbackend.deposit.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Map;

public record UpdateDepositPaymentRouteRequest(
    @JsonProperty("treasury_account_id") Long treasuryAccountId,
    String title,
    @JsonProperty("payment_details") Map<String, Object> paymentDetails,
    @JsonProperty("min_amount") @Positive @Digits(integer = 9, fraction = 4) BigDecimal minAmount,
    @JsonProperty("max_amount") @Positive @Digits(integer = 9, fraction = 4) BigDecimal maxAmount,
    Integer priority,
    @JsonProperty("is_active") Boolean active,
    String note
) {
}
