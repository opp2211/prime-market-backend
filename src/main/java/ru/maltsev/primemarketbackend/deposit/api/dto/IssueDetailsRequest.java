package ru.maltsev.primemarketbackend.deposit.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record IssueDetailsRequest(
    @JsonProperty("payment_details") @NotNull String paymentDetails
) {
}
