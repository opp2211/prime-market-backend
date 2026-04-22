package ru.maltsev.primemarketbackend.account;

import java.math.BigDecimal;
import java.util.Objects;

public final class AccountBalanceSupport {
    private AccountBalanceSupport() {
    }

    public static BigDecimal available(BigDecimal balance, BigDecimal reserved) {
        return balance.subtract(reserved);
    }

    public static BigDecimal increaseReserved(BigDecimal reserved, BigDecimal amount) {
        requirePositiveReservedAmount(amount);
        return reserved.add(amount);
    }

    public static BigDecimal decreaseReserved(BigDecimal reserved, BigDecimal amount) {
        requirePositiveReservedAmount(amount);
        if (reserved.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Reserved amount cannot become negative");
        }
        return reserved.subtract(amount);
    }

    private static void requirePositiveReservedAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Reserved amount must be positive");
        }
    }
}
