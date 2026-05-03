package ru.maltsev.primemarketbackend.currency.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_currency_conversions")
public class UserCurrencyConversion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "from_account_id", nullable = false)
    private Long fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private Long toAccountId;

    @Column(name = "from_currency_code", nullable = false, length = 16)
    private String fromCurrencyCode;

    @Column(name = "to_currency_code", nullable = false, length = 16)
    private String toCurrencyCode;

    @Column(name = "from_amount", nullable = false, precision = 13, scale = 4)
    private BigDecimal fromAmount;

    @Column(name = "to_amount", nullable = false, precision = 13, scale = 4)
    private BigDecimal toAmount;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal rate;

    @Column(name = "rate_source", nullable = false, length = 32)
    private String rateSource;

    @Column(nullable = false, length = 32)
    private String status = "COMPLETED";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    public UserCurrencyConversion(
        Long userId,
        Long fromAccountId,
        Long toAccountId,
        String fromCurrencyCode,
        String toCurrencyCode,
        BigDecimal fromAmount,
        BigDecimal toAmount,
        BigDecimal rate,
        String rateSource
    ) {
        this.userId = userId;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.fromCurrencyCode = fromCurrencyCode;
        this.toCurrencyCode = toCurrencyCode;
        this.fromAmount = fromAmount;
        this.toAmount = toAmount;
        this.rate = rate;
        this.rateSource = rateSource;
        this.status = "COMPLETED";
    }

    @PrePersist
    private void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }
}
