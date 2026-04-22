package ru.maltsev.primemarketbackend.withdrawal.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequest;

public record WithdrawalRequestResponse(
    @JsonProperty("public_id") UUID publicId,
    BigDecimal amount,
    @JsonProperty("actual_payout_amount") BigDecimal actualPayoutAmount,
    @JsonProperty("currency_code") String currencyCode,
    @JsonProperty("withdrawal_method_code") String withdrawalMethodCode,
    @JsonProperty("withdrawal_method_title") String withdrawalMethodTitle,
    @JsonProperty("payout_profile_public_id") UUID payoutProfilePublicId,
    Map<String, Object> requisites,
    @JsonProperty("method_note") String methodNote,
    String status,
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
    public static WithdrawalRequestResponse from(WithdrawalRequest request) {
        return new WithdrawalRequestResponse(
            request.getPublicId(),
            request.getAmount(),
            request.getActualPayoutAmount(),
            request.getCurrencyCodeSnapshot(),
            request.getWithdrawalMethodCodeSnapshot(),
            request.getWithdrawalMethodTitleSnapshot(),
            request.getPayoutProfile() == null ? null : request.getPayoutProfile().getPublicId(),
            request.getRequisitesSnapshot(),
            request.getMethodNoteSnapshot(),
            request.getStatus().name(),
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
