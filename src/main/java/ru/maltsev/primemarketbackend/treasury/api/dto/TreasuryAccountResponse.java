package ru.maltsev.primemarketbackend.treasury.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryAccount;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryAccountType;

public record TreasuryAccountResponse(
    Long id,
    String code,
    String title,
    @JsonProperty("currency_code") String currencyCode,
    @JsonProperty("account_type") TreasuryAccountType accountType,
    BigDecimal balance,
    @JsonProperty("is_active") boolean active,
    Map<String, Object> details,
    String note,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt
) {
    public static TreasuryAccountResponse from(TreasuryAccount account) {
        return new TreasuryAccountResponse(
            account.getId(),
            account.getCode(),
            account.getTitle(),
            account.getCurrencyCode(),
            account.getType(),
            account.getBalance(),
            account.isActive(),
            account.getDetails(),
            account.getNote(),
            account.getCreatedAt(),
            account.getUpdatedAt()
        );
    }
}
