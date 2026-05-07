package ru.maltsev.primemarketbackend.platform.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccountTransaction;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccountTransactionType;

public record PlatformAccountTransactionResponse(
    Long id,
    @JsonProperty("group_key") String groupKey,
    @JsonProperty("platform_account_id") Long platformAccountId,
    @JsonProperty("platform_account_code") String platformAccountCode,
    @JsonProperty("currency_code") String currencyCode,
    BigDecimal amount,
    @JsonProperty("transaction_type") PlatformAccountTransactionType transactionType,
    @JsonProperty("ref_type") String refType,
    @JsonProperty("ref_code") String refCode,
    String description,
    @JsonProperty("actor_user_id") Long actorUserId,
    Map<String, Object> metadata,
    @JsonProperty("created_at") Instant createdAt
) {
    public static PlatformAccountTransactionResponse from(PlatformAccountTransaction transaction) {
        return new PlatformAccountTransactionResponse(
            transaction.getId(),
            transaction.getGroupKey(),
            transaction.getPlatformAccount().getId(),
            transaction.getPlatformAccount().getAccountCode().name(),
            transaction.getPlatformAccount().getCurrencyCode(),
            transaction.getAmount(),
            transaction.getTransactionType(),
            transaction.getRefType(),
            transaction.getRefCode(),
            transaction.getDescription(),
            transaction.getActorUserId(),
            transaction.getMetadata(),
            transaction.getCreatedAt()
        );
    }
}
