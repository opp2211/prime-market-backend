package ru.maltsev.primemarketbackend.deposit.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.Instant;

public record IssueDetailsRequest(
    @JsonProperty("payment_details") String paymentDetails,
    @JsonProperty("deposit_payment_route_id") Long depositPaymentRouteId,
    @JsonProperty("treasury_account_id") Long treasuryAccountId,
    @JsonProperty("treasury_amount")
    @DecimalMin(value = "0.0001", inclusive = true)
    @Digits(integer = 15, fraction = 4)
    BigDecimal treasuryAmount,
    @JsonProperty("expires_at") Instant expiresAt,
    @JsonProperty("operator_comment") String operatorComment
) {
}
