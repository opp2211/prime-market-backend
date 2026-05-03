package ru.maltsev.primemarketbackend.deposit.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ConfirmDepositRequest(
    @JsonProperty("confirmation_reference") String confirmationReference,
    @JsonProperty("operator_comment") String operatorComment
) {
}
