package ru.maltsev.primemarketbackend.platform.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccount;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccountCode;

public record PlatformAccountResponse(
    Long id,
    @JsonProperty("account_code") PlatformAccountCode accountCode,
    String title,
    @JsonProperty("currency_code") String currencyCode,
    BigDecimal balance,
    @JsonProperty("is_active") boolean active,
    String note,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt
) {
    public static PlatformAccountResponse from(PlatformAccount account) {
        return new PlatformAccountResponse(
            account.getId(),
            account.getAccountCode(),
            account.getTitle(),
            account.getCurrencyCode(),
            account.getBalance(),
            account.isActive(),
            account.getNote(),
            account.getCreatedAt(),
            account.getUpdatedAt()
        );
    }
}
