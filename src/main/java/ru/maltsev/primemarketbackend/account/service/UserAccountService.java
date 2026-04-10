package ru.maltsev.primemarketbackend.account.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.account.api.dto.UserAccountResponse;
import ru.maltsev.primemarketbackend.account.api.dto.UserAccountTxShortResponse;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;
import ru.maltsev.primemarketbackend.account.repository.UserAccountRepository;
import ru.maltsev.primemarketbackend.account.repository.UserAccountTxCriteriaRepository;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserAccountService {
    private final UserAccountRepository userAccountRepository;
    private final UserAccountTxCriteriaRepository userAccountTxCriteriaRepository;
    private final UserRepository userRepository;

    public Map<String, UserAccountResponse> getUserAccountsWithPositiveBalance(Long userId) {
        List<UserAccount> accounts = userAccountRepository
                .findAllByUserIdAndBalanceGreaterThan(userId, BigDecimal.ZERO);

        Map<String, UserAccountResponse> response = new LinkedHashMap<>();
        for (UserAccount account : accounts) {
            response.put(account.getCurrencyCode(), UserAccountResponse.from(account));
        }
        return response;
    }

    public Page<UserAccountTxShortResponse> getUserAccountTxs(
            Long userId,
            List<String> currency,
            List<String> type,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        return userAccountTxCriteriaRepository.findUserAccountTxs(
                userId,
                currency,
                type,
                from,
                to,
                pageable
        );
    }

    @Transactional
    public UserAccount getOrCreateAccount(Long userId, String currencyCode) {
        return userAccountRepository.findByUserIdAndCurrencyCode(userId, currencyCode)
            .orElseGet(() -> createAccount(userId, currencyCode));
    }

    @Transactional
    public UserAccount getOrCreateAccountForUpdate(Long userId, String currencyCode) {
        return userAccountRepository.findByUserIdAndCurrencyCodeIgnoreCase(userId, currencyCode)
            .orElseGet(() -> createAccount(userId, currencyCode));
    }

    private UserAccount createAccount(Long userId, String currencyCode) {
        User user = userRepository.getReferenceById(userId);
        UserAccount account = new UserAccount(user, currencyCode);
        try {
            return userAccountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            return userAccountRepository.findByUserIdAndCurrencyCodeIgnoreCase(userId, currencyCode)
                .orElseThrow(() -> ex);
        }
    }
}
