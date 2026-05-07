package ru.maltsev.primemarketbackend.platform.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccountTransactionType;

public record CreatePlatformAccountAdjustmentRequest(
    @JsonProperty("platform_account_id") @NotNull Long platformAccountId,
    @JsonProperty("transaction_type") @NotNull PlatformAccountTransactionType transactionType,
    @NotNull @Digits(integer = 15, fraction = 4) BigDecimal amount,
    String description,
    Map<String, Object> metadata
) {
}
