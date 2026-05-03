package ru.maltsev.primemarketbackend.treasury.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
@Table(name = "treasury_accounts")
public class TreasuryAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(name = "currency_code", nullable = false, length = 16)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    private TreasuryAccountType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> details = new LinkedHashMap<>();

    @Column
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    public TreasuryAccount(
        String code,
        String title,
        String currencyCode,
        TreasuryAccountType type,
        Map<String, Object> details,
        String note,
        boolean active
    ) {
        this.code = normalizeCode(code);
        this.title = normalizeRequired(title);
        this.currencyCode = currencyCode.toUpperCase(Locale.ROOT);
        this.type = type;
        this.details = copyDetails(details);
        this.note = normalizeOptional(note);
        this.active = active;
    }

    public void update(
        String title,
        TreasuryAccountType type,
        Map<String, Object> details,
        String note,
        Boolean active
    ) {
        if (title != null) {
            this.title = normalizeRequired(title);
        }
        if (type != null) {
            this.type = type;
        }
        if (details != null) {
            this.details = copyDetails(details);
        }
        if (note != null) {
            this.note = normalizeOptional(note);
        }
        if (active != null) {
            this.active = active;
        }
    }

    @PrePersist
    private void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }

    private String normalizeCode(String value) {
        return normalizeRequired(value).toUpperCase(Locale.ROOT);
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

    private Map<String, Object> copyDetails(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }
}
