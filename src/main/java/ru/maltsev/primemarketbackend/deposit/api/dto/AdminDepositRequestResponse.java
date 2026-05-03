package ru.maltsev.primemarketbackend.deposit.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequest;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequestStatus;
import ru.maltsev.primemarketbackend.money.api.dto.MoneyOperationEventResponse;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminDepositRequestResponse(
    @JsonProperty("public_id") UUID publicId,
    @JsonProperty("user_id") Long userId,
    BigDecimal amount,
    @JsonProperty("currency_code") String currencyCode,
    @JsonProperty("deposit_method_id") Long depositMethodId,
    @JsonProperty("deposit_method_title") String depositMethodTitle,
    DepositRequestStatus status,
    @JsonProperty("payment_details") String paymentDetails,
    @JsonProperty("details_issued_at") Instant detailsIssuedAt,
    @JsonProperty("details_issued_by_user_id") Long detailsIssuedByUserId,
    @JsonProperty("user_marked_paid_at") Instant userMarkedPaidAt,
    @JsonProperty("confirmed_at") Instant confirmedAt,
    @JsonProperty("confirmed_by_user_id") Long confirmedByUserId,
    @JsonProperty("confirmation_reference") String confirmationReference,
    @JsonProperty("rejected_at") Instant rejectedAt,
    @JsonProperty("rejected_by_user_id") Long rejectedByUserId,
    @JsonProperty("reject_reason") String rejectReason,
    @JsonProperty("operator_comment") String operatorComment,
    @JsonProperty("cancelled_at") Instant cancelledAt,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    List<MoneyOperationEventResponse> events
) {
    public static AdminDepositRequestResponse from(DepositRequest request) {
        return from(request, List.of());
    }

    public static AdminDepositRequestResponse from(DepositRequest request, List<MoneyOperationEvent> events) {
        return new AdminDepositRequestResponse(
            request.getPublicId(),
            request.getUserId(),
            request.getAmount(),
            request.getCurrencyCodeSnapshot(),
            request.getDepositMethod().getId(),
            request.getDepositMethodTitleSnapshot(),
            request.getStatus(),
            request.getPaymentDetails(),
            request.getDetailsIssuedAt(),
            request.getDetailsIssuedByUserId(),
            request.getUserMarkedPaidAt(),
            request.getConfirmedAt(),
            request.getConfirmedByUserId(),
            request.getConfirmationReference(),
            request.getRejectedAt(),
            request.getRejectedByUserId(),
            request.getRejectReason(),
            request.getOperatorComment(),
            request.getCancelledAt(),
            request.getCreatedAt(),
            request.getUpdatedAt(),
            events.stream().map(MoneyOperationEventResponse::from).toList()
        );
    }
}
