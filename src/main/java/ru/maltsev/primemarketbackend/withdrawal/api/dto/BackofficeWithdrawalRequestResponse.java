package ru.maltsev.primemarketbackend.withdrawal.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequest;

public record BackofficeWithdrawalRequestResponse(
    @JsonProperty("public_id") UUID publicId,
    @JsonProperty("user_id") Long userId,
    @JsonProperty("user_account_id") Long userAccountId,
    BigDecimal amount,
    @JsonProperty("actual_payout_amount") BigDecimal actualPayoutAmount,
    @JsonProperty("currency_code") String currencyCode,
    @JsonProperty("withdrawal_method_id") Long withdrawalMethodId,
    @JsonProperty("withdrawal_method_code") String withdrawalMethodCode,
    @JsonProperty("withdrawal_method_title") String withdrawalMethodTitle,
    @JsonProperty("payout_profile_public_id") UUID payoutProfilePublicId,
    Map<String, Object> requisites,
    @JsonProperty("method_note") String methodNote,
    String status,
    @JsonProperty("processed_by_user_id") Long processedByUserId,
    @JsonProperty("operator_comment") String operatorComment,
    @JsonProperty("rejection_reason") String rejectionReason,
    @JsonProperty("opened_at") Instant openedAt,
    @JsonProperty("processing_at") Instant processingAt,
    @JsonProperty("completed_at") Instant completedAt,
    @JsonProperty("cancelled_at") Instant cancelledAt,
    @JsonProperty("rejected_at") Instant rejectedAt,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt
) {
    public static BackofficeWithdrawalRequestResponse from(WithdrawalRequest request) {
        return new BackofficeWithdrawalRequestResponse(
            request.getPublicId(),
            request.getUserId(),
            request.getUserAccountId(),
            request.getAmount(),
            request.getActualPayoutAmount(),
            request.getCurrencyCodeSnapshot(),
            request.getWithdrawalMethod().getId(),
            request.getWithdrawalMethodCodeSnapshot(),
            request.getWithdrawalMethodTitleSnapshot(),
            request.getPayoutProfile() == null ? null : request.getPayoutProfile().getPublicId(),
            request.getRequisitesSnapshot(),
            request.getMethodNoteSnapshot(),
            request.getStatus().name(),
            request.getProcessedByUserId(),
            request.getOperatorComment(),
            request.getRejectionReason(),
            request.getOpenedAt(),
            request.getProcessingAt(),
            request.getCompletedAt(),
            request.getCancelledAt(),
            request.getRejectedAt(),
            request.getCreatedAt(),
            request.getUpdatedAt()
        );
    }
}
