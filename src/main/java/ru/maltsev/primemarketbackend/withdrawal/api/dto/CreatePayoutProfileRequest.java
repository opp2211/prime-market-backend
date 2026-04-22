package ru.maltsev.primemarketbackend.withdrawal.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record CreatePayoutProfileRequest(
    @JsonProperty("withdrawal_method_id") @NotNull Long withdrawalMethodId,
    @NotBlank String title,
    @NotEmpty Map<String, Object> requisites,
    @JsonProperty("is_default") Boolean isDefault
) {
}
