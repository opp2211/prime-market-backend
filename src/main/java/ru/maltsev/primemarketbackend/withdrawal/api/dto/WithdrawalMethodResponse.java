package ru.maltsev.primemarketbackend.withdrawal.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Map;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalMethod;

public record WithdrawalMethodResponse(
    Long id,
    String code,
    String title,
    @JsonProperty("currency_code") String currencyCode,
    @JsonProperty("requisites_schema") Map<String, Object> requisitesSchema,
    @JsonProperty("min_amount") BigDecimal minAmount,
    String note
) {
    public static WithdrawalMethodResponse from(WithdrawalMethod method) {
        return new WithdrawalMethodResponse(
            method.getId(),
            method.getCode(),
            method.getTitle(),
            method.getCurrencyCode(),
            method.getRequisitesSchema(),
            method.getMinAmount(),
            method.getNote()
        );
    }
}
