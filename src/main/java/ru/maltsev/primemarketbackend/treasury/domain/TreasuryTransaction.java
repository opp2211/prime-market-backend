package ru.maltsev.primemarketbackend.treasury.domain;

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
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationType;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "treasury_transactions")
public class TreasuryTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_key", length = 64)
    private String groupKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treasury_account_id", nullable = false)
    private TreasuryAccount treasuryAccount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 64)
    private TreasuryTransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", length = 64)
    private MoneyOperationType operationType;

    @Column(name = "operation_id")
    private Long operationId;

    @Column(name = "operation_code", length = 16)
    private String operationCode;

    @Column(name = "external_reference")
    private String externalReference;

    @Column
    private String description;

    @Column(name = "operator_comment")
    private String operatorComment;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    public TreasuryTransaction(
        String groupKey,
        TreasuryAccount treasuryAccount,
        BigDecimal amount,
        TreasuryTransactionType transactionType,
        MoneyOperationType operationType,
        Long operationId,
        String operationCode,
        String externalReference,
        String description,
        String operatorComment,
        Long actorUserId,
        Map<String, Object> metadata
    ) {
        this.groupKey = groupKey;
        this.treasuryAccount = treasuryAccount;
        this.amount = amount;
        this.transactionType = transactionType;
        this.operationType = operationType;
        this.operationId = operationId;
        this.operationCode = operationCode;
        this.externalReference = normalizeOptional(externalReference);
        this.description = normalizeOptional(description);
        this.operatorComment = normalizeOptional(operatorComment);
        this.actorUserId = actorUserId;
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
