package ru.maltsev.primemarketbackend.deposit.domain;

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
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryAccount;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "deposit_payment_instructions")
public class DepositPaymentInstruction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deposit_request_id", nullable = false)
    private DepositRequest depositRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_payment_route_id")
    private DepositPaymentRoute depositPaymentRoute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treasury_account_id")
    private TreasuryAccount treasuryAccount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_details_snapshot", nullable = false)
    private Map<String, Object> paymentDetailsSnapshot = new LinkedHashMap<>();

    @Column(nullable = false, precision = 13, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code_snapshot", nullable = false, length = 16)
    private String currencyCodeSnapshot;

    @Column(name = "treasury_amount", precision = 19, scale = 4)
    private BigDecimal treasuryAmount;

    @Column(name = "treasury_currency_code_snapshot", length = 16)
    private String treasuryCurrencyCodeSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DepositPaymentInstructionStatus status = DepositPaymentInstructionStatus.ACTIVE;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "issued_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant issuedAt;

    @Column(name = "issued_by_user_id")
    private Long issuedByUserId;

    @Column(name = "operator_comment")
    private String operatorComment;

    public DepositPaymentInstruction(
        DepositRequest depositRequest,
        DepositPaymentRoute depositPaymentRoute,
        TreasuryAccount treasuryAccount,
        Map<String, Object> paymentDetailsSnapshot,
        BigDecimal amount,
        String currencyCodeSnapshot,
        BigDecimal treasuryAmount,
        String treasuryCurrencyCodeSnapshot,
        Instant expiresAt,
        Long issuedByUserId,
        String operatorComment
    ) {
        this.depositRequest = depositRequest;
        this.depositPaymentRoute = depositPaymentRoute;
        this.treasuryAccount = treasuryAccount;
        this.paymentDetailsSnapshot = paymentDetailsSnapshot == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(paymentDetailsSnapshot);
        this.amount = amount;
        this.currencyCodeSnapshot = currencyCodeSnapshot;
        this.treasuryAmount = treasuryAmount;
        this.treasuryCurrencyCodeSnapshot = treasuryCurrencyCodeSnapshot;
        this.expiresAt = expiresAt;
        this.issuedByUserId = issuedByUserId;
        this.operatorComment = normalizeOptional(operatorComment);
        this.status = DepositPaymentInstructionStatus.ACTIVE;
    }

    public void markUsed() {
        status = DepositPaymentInstructionStatus.USED;
    }

    public void cancel() {
        status = DepositPaymentInstructionStatus.CANCELLED;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
