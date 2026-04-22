package ru.maltsev.primemarketbackend.withdrawal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "withdrawal_methods")
public class WithdrawalMethod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(name = "currency_code", nullable = false, length = 16)
    private String currencyCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requisites_schema", nullable = false)
    private Map<String, Object> requisitesSchema = new LinkedHashMap<>();

    @Column(name = "min_amount", precision = 13, scale = 4)
    private BigDecimal minAmount;

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
}
