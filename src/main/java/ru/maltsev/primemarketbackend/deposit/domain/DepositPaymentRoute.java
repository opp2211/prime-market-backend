package ru.maltsev.primemarketbackend.deposit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "deposit_payment_routes")
public class DepositPaymentRoute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deposit_method_id", nullable = false)
    private DepositMethod depositMethod;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treasury_account_id", nullable = false)
    private TreasuryAccount treasuryAccount;

    @Column(nullable = false)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_details", nullable = false)
    private Map<String, Object> paymentDetails = new LinkedHashMap<>();

    @Column(name = "min_amount", precision = 13, scale = 4)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 13, scale = 4)
    private BigDecimal maxAmount;

    @Column(nullable = false)
    private int priority = 100;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    public DepositPaymentRoute(
        DepositMethod depositMethod,
        TreasuryAccount treasuryAccount,
        String title,
        Map<String, Object> paymentDetails,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        Integer priority,
        Boolean active,
        String note
    ) {
        this.depositMethod = depositMethod;
        this.treasuryAccount = treasuryAccount;
        this.title = normalizeRequired(title);
        this.paymentDetails = copyDetails(paymentDetails);
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.priority = priority == null ? 100 : priority;
        this.active = active == null || active;
        this.note = normalizeOptional(note);
    }

    public void update(
        TreasuryAccount treasuryAccount,
        String title,
        Map<String, Object> paymentDetails,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        Integer priority,
        Boolean active,
        String note
    ) {
        if (treasuryAccount != null) {
            this.treasuryAccount = treasuryAccount;
        }
        if (title != null) {
            this.title = normalizeRequired(title);
        }
        if (paymentDetails != null) {
            this.paymentDetails = copyDetails(paymentDetails);
        }
        if (minAmount != null) {
            this.minAmount = minAmount;
        }
        if (maxAmount != null) {
            this.maxAmount = maxAmount;
        }
        if (priority != null) {
            this.priority = priority;
        }
        if (active != null) {
            this.active = active;
        }
        if (note != null) {
            this.note = normalizeOptional(note);
        }
    }

    public boolean accepts(BigDecimal amount) {
        return (minAmount == null || minAmount.compareTo(amount) <= 0)
            && (maxAmount == null || maxAmount.compareTo(amount) >= 0);
    }

    private Map<String, Object> copyDetails(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    private String normalizeRequired(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Value is required");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
