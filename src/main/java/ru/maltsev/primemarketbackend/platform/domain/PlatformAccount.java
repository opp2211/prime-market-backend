package ru.maltsev.primemarketbackend.platform.domain;

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
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import ru.maltsev.primemarketbackend.account.AccountBalanceSupport;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "platform_accounts")
public class PlatformAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_code", nullable = false, length = 64)
    private PlatformAccountCode accountCode = PlatformAccountCode.FEES;

    @Column(nullable = false)
    private String title;

    @Column(name = "currency_code", nullable = false, length = 16)
    private String currencyCode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal reserved = BigDecimal.ZERO;

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

    public PlatformAccount(PlatformAccountCode accountCode, String currencyCode, String title, String note) {
        this.accountCode = accountCode;
        this.currencyCode = currencyCode.toUpperCase(Locale.ROOT);
        this.title = normalizeRequired(title);
        this.note = normalizeOptional(note);
        this.active = true;
    }

    public PlatformAccount(String currencyCode) {
        this(
            PlatformAccountCode.FEES,
            currencyCode,
            "Platform fees " + currencyCode.toUpperCase(Locale.ROOT),
            null
        );
    }

    public void update(String title, String note, Boolean active) {
        if (title != null) {
            this.title = normalizeRequired(title);
        }
        if (note != null) {
            this.note = normalizeOptional(note);
        }
        if (active != null) {
            this.active = active;
        }
    }

    public BigDecimal available() {
        return AccountBalanceSupport.available(balance, reserved);
    }

    public void increaseReserved(BigDecimal amount) {
        reserved = AccountBalanceSupport.increaseReserved(reserved, amount);
    }

    public void decreaseReserved(BigDecimal amount) {
        reserved = AccountBalanceSupport.decreaseReserved(reserved, amount);
    }

    @PrePersist
    private void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
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
