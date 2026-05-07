package ru.maltsev.primemarketbackend.withdrawal.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record ConfirmWithdrawalRequest(
    @JsonProperty("actual_payout_amount")
    @DecimalMin(value = "0.0001", inclusive = true)
    @Digits(integer = 9, fraction = 4)
    BigDecimal actualPayoutAmount,
    @JsonProperty("operator_comment") String operatorComment,
    @JsonProperty("treasury_account_id") Long treasuryAccountId,
    @JsonProperty("treasury_amount")
    @DecimalMin(value = "0.0001", inclusive = true)
    @Digits(integer = 15, fraction = 4)
    BigDecimal treasuryAmount,
    @JsonProperty("treasury_external_reference") String treasuryExternalReference
) {
}
