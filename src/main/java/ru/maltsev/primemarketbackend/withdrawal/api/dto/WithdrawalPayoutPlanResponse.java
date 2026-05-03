package ru.maltsev.primemarketbackend.withdrawal.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalPayoutPlan;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalPayoutPlanStatus;

public record WithdrawalPayoutPlanResponse(
    @JsonProperty("public_id") UUID publicId,
    @JsonProperty("treasury_account_public_id") UUID treasuryAccountPublicId,
    @JsonProperty("treasury_account_code") String treasuryAccountCode,
    @JsonProperty("treasury_account_title") String treasuryAccountTitle,
    @JsonProperty("planned_user_amount") BigDecimal plannedUserAmount,
    @JsonProperty("user_currency_code") String userCurrencyCode,
    @JsonProperty("treasury_amount") BigDecimal treasuryAmount,
    @JsonProperty("treasury_currency_code") String treasuryCurrencyCode,
    @JsonProperty("external_reference") String externalReference,
    @JsonProperty("operator_comment") String operatorComment,
    WithdrawalPayoutPlanStatus status,
    @JsonProperty("planned_by_user_id") Long plannedByUserId,
    @JsonProperty("planned_at") Instant plannedAt,
    @JsonProperty("completed_at") Instant completedAt,
    @JsonProperty("cancelled_at") Instant cancelledAt
) {
    public static WithdrawalPayoutPlanResponse from(WithdrawalPayoutPlan plan) {
        if (plan == null) {
            return null;
        }
        return new WithdrawalPayoutPlanResponse(
            plan.getPublicId(),
            plan.getTreasuryAccount().getPublicId(),
            plan.getTreasuryAccount().getCode(),
            plan.getTreasuryAccount().getTitle(),
            plan.getPlannedUserAmount(),
            plan.getUserCurrencyCodeSnapshot(),
            plan.getTreasuryAmount(),
            plan.getTreasuryCurrencyCodeSnapshot(),
            plan.getExternalReference(),
            plan.getOperatorComment(),
            plan.getStatus(),
            plan.getPlannedByUserId(),
            plan.getPlannedAt(),
            plan.getCompletedAt(),
            plan.getCancelledAt()
        );
    }
}
