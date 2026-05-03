package ru.maltsev.primemarketbackend.treasury.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryAccountType;

public record CreateTreasuryAccountRequest(
    @NotBlank String code,
    @NotBlank String title,
    @JsonProperty("currency_code") @NotBlank String currencyCode,
    @JsonProperty("account_type") @NotNull TreasuryAccountType accountType,
    Map<String, Object> details,
    String note,
    @JsonProperty("is_active") Boolean active
) {
}
