package ru.maltsev.primemarketbackend.treasury.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationType;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryTransaction;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryTransactionType;

public record TreasuryTransactionResponse(
    Long id,
    @JsonProperty("group_key") String groupKey,
    @JsonProperty("treasury_account_id") Long treasuryAccountId,
    @JsonProperty("treasury_account_code") String treasuryAccountCode,
    @JsonProperty("treasury_account_title") String treasuryAccountTitle,
    @JsonProperty("currency_code") String currencyCode,
    BigDecimal amount,
    @JsonProperty("transaction_type") TreasuryTransactionType transactionType,
    @JsonProperty("operation_type") MoneyOperationType operationType,
    @JsonProperty("operation_code") String operationCode,
    @JsonProperty("external_reference") String externalReference,
    String description,
    @JsonProperty("operator_comment") String operatorComment,
    @JsonProperty("actor_user_id") Long actorUserId,
    Map<String, Object> metadata,
    @JsonProperty("created_at") Instant createdAt
) {
    public static TreasuryTransactionResponse from(TreasuryTransaction transaction) {
        return new TreasuryTransactionResponse(
            transaction.getId(),
            transaction.getGroupKey(),
            transaction.getTreasuryAccount().getId(),
            transaction.getTreasuryAccount().getCode(),
            transaction.getTreasuryAccount().getTitle(),
            transaction.getTreasuryAccount().getCurrencyCode(),
            transaction.getAmount(),
            transaction.getTransactionType(),
            transaction.getOperationType(),
            transaction.getOperationCode(),
            transaction.getExternalReference(),
            transaction.getDescription(),
            transaction.getOperatorComment(),
            transaction.getActorUserId(),
            transaction.getMetadata(),
            transaction.getCreatedAt()
        );
    }
}
