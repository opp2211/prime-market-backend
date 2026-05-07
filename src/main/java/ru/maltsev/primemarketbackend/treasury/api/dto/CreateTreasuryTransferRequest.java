package ru.maltsev.primemarketbackend.treasury.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

public record CreateTreasuryTransferRequest(
    @JsonProperty("from_account_id") @NotNull Long fromAccountId,
    @JsonProperty("to_account_id") @NotNull Long toAccountId,
    @JsonProperty("from_amount")
    @NotNull
    @DecimalMin(value = "0.0001", inclusive = true)
    @Digits(integer = 15, fraction = 4)
    BigDecimal fromAmount,
    @JsonProperty("to_amount")
    @NotNull
    @DecimalMin(value = "0.0001", inclusive = true)
    @Digits(integer = 15, fraction = 4)
    BigDecimal toAmount,
    @JsonProperty("external_reference") String externalReference,
    String description,
    @JsonProperty("operator_comment") String operatorComment,
    Map<String, Object> metadata
) {
}
