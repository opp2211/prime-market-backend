package ru.maltsev.primemarketbackend.withdrawal.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import ru.maltsev.primemarketbackend.money.api.dto.MoneyOperationEventResponse;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationEvent;
import ru.maltsev.primemarketbackend.treasury.api.dto.TreasuryTransactionResponse;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryTransaction;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequest;

public record BackofficeWithdrawalRequestResponse(
    @JsonProperty("public_code") String publicCode,
    @JsonProperty("user_id") Long userId,
    @JsonProperty("user_account_id") Long userAccountId,
    BigDecimal amount,
    @JsonProperty("actual_payout_amount") BigDecimal actualPayoutAmount,
    @JsonProperty("currency_code") String currencyCode,
    @JsonProperty("withdrawal_method_id") Long withdrawalMethodId,
    @JsonProperty("withdrawal_method_code") String withdrawalMethodCode,
    @JsonProperty("withdrawal_method_title") String withdrawalMethodTitle,
    @JsonProperty("payout_profile_id") Long payoutProfileId,
    Map<String, Object> requisites,
    @JsonProperty("method_note") String methodNote,
    String status,
    @JsonProperty("processed_by_user_id") Long processedByUserId,
    @JsonProperty("operator_comment") String operatorComment,
    @JsonProperty("rejection_reason") String rejectionReason,
    @JsonProperty("treasury_account_id") Long treasuryAccountId,
    @JsonProperty("treasury_transaction_id") Long treasuryTransactionId,
    @JsonProperty("payout_plan") WithdrawalPayoutPlanResponse payoutPlan,
    @JsonProperty("opened_at") Instant openedAt,
    @JsonProperty("processing_at") Instant processingAt,
    @JsonProperty("completed_at") Instant completedAt,
    @JsonProperty("cancelled_at") Instant cancelledAt,
    @JsonProperty("rejected_at") Instant rejectedAt,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    List<MoneyOperationEventResponse> events,
    @JsonProperty("treasury_transactions") List<TreasuryTransactionResponse> treasuryTransactions
) {
    public static BackofficeWithdrawalRequestResponse from(WithdrawalRequest request) {
        return from(request, List.of());
    }

    public static BackofficeWithdrawalRequestResponse from(
        WithdrawalRequest request,
        List<MoneyOperationEvent> events
    ) {
        return from(request, events, List.of());
    }

    public static BackofficeWithdrawalRequestResponse from(
        WithdrawalRequest request,
        List<MoneyOperationEvent> events,
        List<TreasuryTransaction> treasuryTransactions
    ) {
        return from(request, events, treasuryTransactions, null);
    }

    public static BackofficeWithdrawalRequestResponse from(
        WithdrawalRequest request,
        List<MoneyOperationEvent> events,
        List<TreasuryTransaction> treasuryTransactions,
        ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalPayoutPlan payoutPlan
    ) {
        return new BackofficeWithdrawalRequestResponse(
            request.getPublicCode(),
            request.getUserId(),
            request.getUserAccountId(),
            request.getAmount(),
            request.getActualPayoutAmount(),
            request.getCurrencyCodeSnapshot(),
            request.getWithdrawalMethod().getId(),
            request.getWithdrawalMethodCodeSnapshot(),
            request.getWithdrawalMethodTitleSnapshot(),
            request.getPayoutProfile() == null ? null : request.getPayoutProfile().getId(),
            request.getRequisitesSnapshot(),
            request.getMethodNoteSnapshot(),
            request.getStatus().name(),
            request.getProcessedByUserId(),
            request.getOperatorComment(),
            request.getRejectionReason(),
            request.getTreasuryAccountId(),
            request.getTreasuryTransactionId(),
            WithdrawalPayoutPlanResponse.from(payoutPlan),
            request.getOpenedAt(),
            request.getProcessingAt(),
            request.getCompletedAt(),
            request.getCancelledAt(),
            request.getRejectedAt(),
            request.getCreatedAt(),
            request.getUpdatedAt(),
            events.stream().map(MoneyOperationEventResponse::from).toList(),
            treasuryTransactions.stream().map(TreasuryTransactionResponse::from).toList()
        );
    }
}
