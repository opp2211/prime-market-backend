package ru.maltsev.primemarketbackend.deposit.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.maltsev.primemarketbackend.deposit.domain.DepositMethod;

public record DepositMethodResponse(
    Long id,
    String title,
    @JsonProperty("currency_code") String currencyCode,
    @JsonProperty("auto_confirmation") boolean autoConfirmation
) {
    public static DepositMethodResponse from(DepositMethod method) {
        return new DepositMethodResponse(
            method.getId(),
            method.getTitle(),
            method.getCurrencyCode(),
            method.isAutoConfirmation()
        );
    }
}
