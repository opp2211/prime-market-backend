package ru.maltsev.primemarketbackend.account.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.maltsev.primemarketbackend.account.api.dto.UserAccountResponse;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;
import ru.maltsev.primemarketbackend.account.repository.UserAccountRepository;

@Service
@RequiredArgsConstructor
public class UserAccountService {
    private final UserAccountRepository userAccountRepository;

    public Map<String, UserAccountResponse> getUserAccounts(Long userId) {
        List<UserAccount> accounts = userAccountRepository.findAllByUserId(userId);

        Map<String, UserAccountResponse> response = new LinkedHashMap<>();
        for (UserAccount account : accounts) {
            response.put(account.getCurrencyCode(), UserAccountResponse.from(account));
        }
        return response;
    }
}