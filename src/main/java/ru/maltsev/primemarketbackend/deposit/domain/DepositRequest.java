package ru.maltsev.primemarketbackend.deposit.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryTransaction;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "deposit_requests")
public class DepositRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", nullable = false, precision = 13, scale = 4)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deposit_method_id", nullable = false)
    private DepositMethod depositMethod;

    @Column(name = "currency_code_snapshot", nullable = false, length = 16)
    private String currencyCodeSnapshot;

    @Column(name = "deposit_method_title_snapshot", nullable = false)
    private String depositMethodTitleSnapshot;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private DepositRequestStatus status = DepositRequestStatus.PENDING_DETAILS;

    @Column(name = "payment_details")
    @JdbcTypeCode(SqlTypes.JSON)
    private String paymentDetails;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    @Column(name = "details_issued_at")
    private Instant detailsIssuedAt;

    @Column(name = "details_issued_by_user_id")
    private Long detailsIssuedByUserId;

    @Column(name = "user_marked_paid_at")
    private Instant userMarkedPaidAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by_user_id")
    private Long confirmedByUserId;

    @Column(name = "confirmation_reference")
    private String confirmationReference;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejected_by_user_id")
    private Long rejectedByUserId;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "operator_comment")
    private String operatorComment;

    @Column(name = "treasury_account_id")
    private Long treasuryAccountId;

    @Column(name = "treasury_transaction_id")
    private Long treasuryTransactionId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public DepositRequest(Long userId, DepositMethod depositMethod, BigDecimal amount) {
        this.userId = userId;
        this.depositMethod = depositMethod;
        this.amount = amount;
        this.currencyCodeSnapshot = depositMethod.getCurrencyCode();
        this.depositMethodTitleSnapshot = Objects.requireNonNullElse(depositMethod.getTitle(), "");
    }

    public void startPendingDetails() {
        status = DepositRequestStatus.PENDING_DETAILS;
    }

    public void startWaitingPayment(String details, Long actorUserId, String comment) {
        status = DepositRequestStatus.WAITING_PAYMENT;
        paymentDetails = details;
        detailsIssuedAt = Instant.now();
        detailsIssuedByUserId = actorUserId;
        operatorComment = normalize(comment);
    }

    public void markPaid() {
        status = DepositRequestStatus.PAYMENT_VERIFICATION;
        userMarkedPaidAt = Instant.now();
    }

    public void confirm(Long actorUserId, String reference, String comment) {
        status = DepositRequestStatus.CONFIRMED;
        confirmedAt = Instant.now();
        confirmedByUserId = actorUserId;
        confirmationReference = normalize(reference);
        operatorComment = normalize(comment);
    }

    public void attachTreasuryTransaction(TreasuryTransaction transaction) {
        if (transaction == null) {
            return;
        }
        treasuryAccountId = transaction.getTreasuryAccount().getId();
        treasuryTransactionId = transaction.getId();
    }

    public void reject(Long actorUserId, String reason, String comment) {
        status = DepositRequestStatus.REJECTED;
        rejectedAt = Instant.now();
        rejectedByUserId = actorUserId;
        rejectReason = reason;
        operatorComment = normalize(comment);
    }

    public void cancel() {
        status = DepositRequestStatus.CANCELLED;
        cancelledAt = Instant.now();
    }

    @PrePersist
    private void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
