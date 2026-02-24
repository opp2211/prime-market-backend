package ru.maltsev.primemarketbackend.account.api.dto;

import java.math.BigDecimal;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;

public record UserAccountResponse(BigDecimal balance, BigDecimal reserved, BigDecimal available) {
    public static UserAccountResponse from(UserAccount account) {
        BigDecimal available = account.available();
        return new UserAccountResponse(account.getBalance(), account.getReserved(), available);
    }
}