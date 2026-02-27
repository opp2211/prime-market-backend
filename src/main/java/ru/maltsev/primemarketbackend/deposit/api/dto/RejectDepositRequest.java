package ru.maltsev.primemarketbackend.deposit.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record RejectDepositRequest(
    @JsonProperty("reject_reason") @NotBlank String rejectReason
) {
}
