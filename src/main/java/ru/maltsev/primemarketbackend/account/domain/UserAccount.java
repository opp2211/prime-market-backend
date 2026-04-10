package ru.maltsev.primemarketbackend.account.domain;

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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.maltsev.primemarketbackend.user.domain.User;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_accounts")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "currency_code", nullable = false, length = 5)
    private String currencyCode;

    @Column(name = "balance", nullable = false, precision = 13, scale = 4)
    private BigDecimal balance;

    @Column(name = "reserved", nullable = false, precision = 13, scale = 4)
    private BigDecimal reserved;

    public UserAccount(User user, String currencyCode) {
        this.user = user;
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
