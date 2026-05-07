package ru.maltsev.primemarketbackend.withdrawal.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateWithdrawalPayoutPlanRequest(
    @JsonProperty("treasury_account_id") @NotNull Long treasuryAccountId,
    @JsonProperty("planned_user_amount")
    @DecimalMin(value = "0.0001", inclusive = true)
    @Digits(integer = 9, fraction = 4)
    BigDecimal plannedUserAmount,
    @JsonProperty("treasury_amount")
    @DecimalMin(value = "0.0001", inclusive = true)
    @Digits(integer = 15, fraction = 4)
    BigDecimal treasuryAmount,
    @JsonProperty("external_reference") String externalReference,
    @JsonProperty("operator_comment") String operatorComment
) {
}
