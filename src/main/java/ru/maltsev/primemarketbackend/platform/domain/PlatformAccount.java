package ru.maltsev.primemarketbackend.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "platform_accounts")
public class PlatformAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency_code", nullable = false, length = 5, unique = true)
    private String currencyCode;

    @Column(name = "balance", nullable = false, precision = 13, scale = 4)
    private BigDecimal balance;

    @Column(name = "reserved", nullable = false, precision = 13, scale = 4)
    private BigDecimal reserved;

    public PlatformAccount(String currencyCode) {
        this.currencyCode = currencyCode;
        this.balance = BigDecimal.ZERO;
        this.reserved = BigDecimal.ZERO;
    }

    public BigDecimal available() {
        return balance.subtract(reserved);
    }

    public void increaseReserved(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Reserved amount must be positive");
        }
        reserved = reserved.add(amount);
    }

    public void decreaseReserved(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Reserved amount must be positive");
        }
        if (reserved.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Reserved amount cannot become negative");
        }
        reserved = reserved.subtract(amount);
    }
}
