package ru.maltsev.primemarketbackend.account.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record WalletItemResponse(
    @JsonProperty("currency_code") String currencyCode,
    @JsonProperty("currency_title") String currencyTitle,
    @JsonProperty("account_exists") boolean accountExists,
    BigDecimal balance,
    BigDecimal reserved,
    BigDecimal available
) {
}
