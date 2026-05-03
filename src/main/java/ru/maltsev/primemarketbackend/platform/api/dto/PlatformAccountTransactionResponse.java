package ru.maltsev.primemarketbackend.platform.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccountTransaction;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccountTransactionType;

public record PlatformAccountTransactionResponse(
    @JsonProperty("public_id") UUID publicId,
    @JsonProperty("group_public_id") UUID groupPublicId,
    @JsonProperty("platform_account_public_id") UUID platformAccountPublicId,
    @JsonProperty("platform_account_code") String platformAccountCode,
    @JsonProperty("currency_code") String currencyCode,
    BigDecimal amount,
    @JsonProperty("transaction_type") PlatformAccountTransactionType transactionType,
    @JsonProperty("ref_type") String refType,
    @JsonProperty("ref_public_id") UUID refPublicId,
    String description,
    @JsonProperty("actor_user_id") Long actorUserId,
    Map<String, Object> metadata,
    @JsonProperty("created_at") Instant createdAt
) {
    public static PlatformAccountTransactionResponse from(PlatformAccountTransaction transaction) {
        return new PlatformAccountTransactionResponse(
            transaction.getPublicId(),
            transaction.getGroupPublicId(),
            transaction.getPlatformAccount().getPublicId(),
            transaction.getPlatformAccount().getAccountCode().name(),
            transaction.getPlatformAccount().getCurrencyCode(),
            transaction.getAmount(),
            transaction.getTransactionType(),
            transaction.getRefType(),
            transaction.getRefPublicId(),
            transaction.getDescription(),
            transaction.getActorUserId(),
            transaction.getMetadata(),
            transaction.getCreatedAt()
        );
    }
}
