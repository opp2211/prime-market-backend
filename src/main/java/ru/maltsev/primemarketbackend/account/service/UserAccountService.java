package ru.maltsev.primemarketbackend.account.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ru.maltsev.primemarketbackend.account.api.dto.UserAccountResponse;
import ru.maltsev.primemarketbackend.account.api.dto.UserAccountTxResponse;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;
import ru.maltsev.primemarketbackend.account.repository.UserAccountRepository;
import ru.maltsev.primemarketbackend.account.repository.UserAccountTxRepository;

@Service
@RequiredArgsConstructor
public class UserAccountService {
    private final UserAccountRepository userAccountRepository;
    private final UserAccountTxRepository userAccountTxRepository;

    public Map<String, UserAccountResponse> getUserAccountsWithPositiveBalance(Long userId) {
        List<UserAccount> accounts = userAccountRepository
                .findAllByUserIdAndBalanceGreaterThan(userId, BigDecimal.ZERO);

        Map<String, UserAccountResponse> response = new LinkedHashMap<>();
        for (UserAccount account : accounts) {
            response.put(account.getCurrencyCode(), UserAccountResponse.from(account));
        }
        return response;
    }

    public Page<UserAccountTxResponse> getUserAccountTxs(
            Long userId,
            String currency,
            String type,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        return userAccountTxRepository.findUserAccountTxs(userId, currency, type, from, to, pageable);
    }
}
