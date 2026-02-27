package ru.maltsev.primemarketbackend.deposit.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateDepositRequest(
    @JsonProperty("deposit_method_id") @NotNull Long depositMethodId,
    @NotNull @DecimalMin("0.0001") BigDecimal amount
) {
}
