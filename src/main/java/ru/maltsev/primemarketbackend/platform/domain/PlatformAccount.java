package ru.maltsev.primemarketbackend.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.maltsev.primemarketbackend.account.AccountBalanceSupport;

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
        return AccountBalanceSupport.available(balance, reserved);
    }

    public void increaseReserved(BigDecimal amount) {
        reserved = AccountBalanceSupport.increaseReserved(reserved, amount);
    }

    public void decreaseReserved(BigDecimal amount) {
        reserved = AccountBalanceSupport.decreaseReserved(reserved, amount);
    }
}
