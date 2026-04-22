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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "withdrawal_requests")
public class WithdrawalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_account_id", nullable = false)
    private Long userAccountId;

    @Column(name = "currency_code_snapshot", nullable = false, length = 16)
    private String currencyCodeSnapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "withdrawal_method_id", nullable = false)
    private WithdrawalMethod withdrawalMethod;

    @Column(name = "withdrawal_method_code_snapshot", nullable = false, length = 64)
    private String withdrawalMethodCodeSnapshot;

    @Column(name = "withdrawal_method_title_snapshot", nullable = false)
    private String withdrawalMethodTitleSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_profile_id")
    private PayoutProfile payoutProfile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requisites_snapshot", nullable = false)
    private Map<String, Object> requisitesSnapshot = new LinkedHashMap<>();

    @Column(nullable = false, precision = 13, scale = 4)
    private BigDecimal amount;

    @Column(name = "actual_payout_amount", precision = 13, scale = 4)
    private BigDecimal actualPayoutAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private WithdrawalRequestStatus status = WithdrawalRequestStatus.OPEN;

    @Column(name = "method_note_snapshot")
    private String methodNoteSnapshot;

    @Column(name = "operator_comment")
    private String operatorComment;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "opened_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant openedAt;

    @Column(name = "processing_at")
    private Instant processingAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "processed_by_user_id")
    private Long processedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    public WithdrawalRequest(
        Long userId,
        Long userAccountId,
        WithdrawalMethod withdrawalMethod,
        PayoutProfile payoutProfile,
        Map<String, Object> requisitesSnapshot,
        BigDecimal amount,
        BigDecimal actualPayoutAmount
    ) {
        this.userId = userId;
        this.userAccountId = userAccountId;
        this.currencyCodeSnapshot = withdrawalMethod.getCurrencyCode();
        this.withdrawalMethod = withdrawalMethod;
        this.withdrawalMethodCodeSnapshot = withdrawalMethod.getCode();
        this.withdrawalMethodTitleSnapshot = withdrawalMethod.getTitle();
        this.payoutProfile = payoutProfile;
        this.requisitesSnapshot = new LinkedHashMap<>(requisitesSnapshot);
        this.amount = amount;
        this.actualPayoutAmount = actualPayoutAmount;
        this.methodNoteSnapshot = withdrawalMethod.getNote();
        this.status = WithdrawalRequestStatus.OPEN;
    }

    public boolean isOpen() {
        return status == WithdrawalRequestStatus.OPEN;
    }

    public boolean isProcessing() {
        return status == WithdrawalRequestStatus.PROCESSING;
    }

    public boolean isCompleted() {
        return status == WithdrawalRequestStatus.COMPLETED;
    }

    public boolean isCancelled() {
        return status == WithdrawalRequestStatus.CANCELLED;
    }

    public boolean isRejected() {
        return status == WithdrawalRequestStatus.REJECTED;
    }

    public void markProcessing(Long processedByUserId, Instant now) {
        this.status = WithdrawalRequestStatus.PROCESSING;
        this.processingAt = now;
        this.processedByUserId = processedByUserId;
    }

    public void cancel(Instant now) {
        this.status = WithdrawalRequestStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public void reject(Long actorUserId, String rejectionReason, String operatorComment, Instant now) {
        this.status = WithdrawalRequestStatus.REJECTED;
        this.rejectionReason = rejectionReason;
        this.operatorComment = operatorComment;
        this.rejectedAt = now;
        this.processedByUserId = Objects.requireNonNullElse(this.processedByUserId, actorUserId);
    }

    public void confirm(Long actorUserId, BigDecimal actualPayoutAmount, String operatorComment, Instant now) {
        this.status = WithdrawalRequestStatus.COMPLETED;
        this.actualPayoutAmount = actualPayoutAmount;
        this.operatorComment = operatorComment;
        this.completedAt = now;
        this.processedByUserId = Objects.requireNonNullElse(this.processedByUserId, actorUserId);
    }

    @PrePersist
    private void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }
}
