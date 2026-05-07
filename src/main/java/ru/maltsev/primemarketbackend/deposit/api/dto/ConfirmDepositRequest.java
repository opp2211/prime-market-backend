package ru.maltsev.primemarketbackend.deposit.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

public record ConfirmDepositRequest(
    @JsonProperty("confirmation_reference") String confirmationReference,
    @JsonProperty("operator_comment") String operatorComment,
    @JsonProperty("treasury_account_id") Long treasuryAccountId,
    @JsonProperty("treasury_amount")
    @DecimalMin(value = "0.0001", inclusive = true)
    @Digits(integer = 15, fraction = 4)
    BigDecimal treasuryAmount,
    @JsonProperty("treasury_external_reference") String treasuryExternalReference
) {
}
