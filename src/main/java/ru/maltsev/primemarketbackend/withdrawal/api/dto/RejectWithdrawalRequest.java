package ru.maltsev.primemarketbackend.withdrawal.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record RejectWithdrawalRequest(
    @JsonProperty("rejection_reason") @NotBlank String rejectionReason,
    @JsonProperty("operator_comment") String operatorComment
) {
}
