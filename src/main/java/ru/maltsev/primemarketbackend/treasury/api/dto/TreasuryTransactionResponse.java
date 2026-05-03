package ru.maltsev.primemarketbackend.treasury.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationType;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryTransaction;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryTransactionType;

public record TreasuryTransactionResponse(
    @JsonProperty("public_id") UUID publicId,
    @JsonProperty("group_public_id") UUID groupPublicId,
    @JsonProperty("treasury_account_public_id") UUID treasuryAccountPublicId,
    @JsonProperty("treasury_account_code") String treasuryAccountCode,
    @JsonProperty("treasury_account_title") String treasuryAccountTitle,
    @JsonProperty("currency_code") String currencyCode,
    BigDecimal amount,
    @JsonProperty("transaction_type") TreasuryTransactionType transactionType,
    @JsonProperty("operation_type") MoneyOperationType operationType,
    @JsonProperty("operation_public_id") UUID operationPublicId,
    @JsonProperty("external_reference") String externalReference,
    String description,
    @JsonProperty("operator_comment") String operatorComment,
    @JsonProperty("actor_user_id") Long actorUserId,
    Map<String, Object> metadata,
    @JsonProperty("created_at") Instant createdAt
) {
    public static TreasuryTransactionResponse from(TreasuryTransaction transaction) {
        return new TreasuryTransactionResponse(
            transaction.getPublicId(),
            transaction.getGroupPublicId(),
            transaction.getTreasuryAccount().getPublicId(),
            transaction.getTreasuryAccount().getCode(),
            transaction.getTreasuryAccount().getTitle(),
            transaction.getTreasuryAccount().getCurrencyCode(),
            transaction.getAmount(),
            transaction.getTransactionType(),
            transaction.getOperationType(),
            transaction.getOperationPublicId(),
            transaction.getExternalReference(),
            transaction.getDescription(),
            transaction.getOperatorComment(),
            transaction.getActorUserId(),
            transaction.getMetadata(),
            transaction.getCreatedAt()
        );
    }
}
