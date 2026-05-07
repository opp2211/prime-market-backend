package ru.maltsev.primemarketbackend.withdrawal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryAccount;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "withdrawal_payout_plans")
public class WithdrawalPayoutPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "withdrawal_request_id", nullable = false)
    private WithdrawalRequest withdrawalRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treasury_account_id", nullable = false)
    private TreasuryAccount treasuryAccount;

    @Column(name = "planned_user_amount", nullable = false, precision = 13, scale = 4)
    private BigDecimal plannedUserAmount;

    @Column(name = "user_currency_code_snapshot", nullable = false, length = 16)
    private String userCurrencyCodeSnapshot;

    @Column(name = "treasury_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal treasuryAmount;

    @Column(name = "treasury_currency_code_snapshot", nullable = false, length = 16)
    private String treasuryCurrencyCodeSnapshot;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "operator_comment")
    private String operatorComment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WithdrawalPayoutPlanStatus status = WithdrawalPayoutPlanStatus.PLANNED;

    @Column(name = "planned_by_user_id")
    private Long plannedByUserId;

    @Column(name = "planned_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant plannedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public WithdrawalPayoutPlan(
        WithdrawalRequest withdrawalRequest,
        TreasuryAccount treasuryAccount,
        BigDecimal plannedUserAmount,
        String userCurrencyCodeSnapshot,
        BigDecimal treasuryAmount,
        Long plannedByUserId,
        String externalReference,
        String operatorComment
    ) {
        this.withdrawalRequest = withdrawalRequest;
        this.treasuryAccount = treasuryAccount;
        this.plannedUserAmount = plannedUserAmount;
        this.userCurrencyCodeSnapshot = userCurrencyCodeSnapshot;
        this.treasuryAmount = treasuryAmount;
        this.treasuryCurrencyCodeSnapshot = treasuryAccount.getCurrencyCode();
        this.plannedByUserId = plannedByUserId;
        this.externalReference = normalizeOptional(externalReference);
        this.operatorComment = normalizeOptional(operatorComment);
        this.status = WithdrawalPayoutPlanStatus.PLANNED;
    }

    public void replace(
        TreasuryAccount treasuryAccount,
        BigDecimal plannedUserAmount,
        BigDecimal treasuryAmount,
        Long plannedByUserId,
        String externalReference,
        String operatorComment
    ) {
        this.treasuryAccount = treasuryAccount;
        this.plannedUserAmount = plannedUserAmount;
        this.treasuryAmount = treasuryAmount;
        this.treasuryCurrencyCodeSnapshot = treasuryAccount.getCurrencyCode();
        this.plannedByUserId = plannedByUserId;
        this.externalReference = normalizeOptional(externalReference);
        this.operatorComment = normalizeOptional(operatorComment);
        this.status = WithdrawalPayoutPlanStatus.PLANNED;
        this.completedAt = null;
        this.cancelledAt = null;
    }

    public void complete(Instant now) {
        status = WithdrawalPayoutPlanStatus.COMPLETED;
        completedAt = now;
    }

    public void cancel(Instant now) {
        status = WithdrawalPayoutPlanStatus.CANCELLED;
        cancelledAt = now;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
