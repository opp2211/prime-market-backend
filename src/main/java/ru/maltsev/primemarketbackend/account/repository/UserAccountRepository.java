package ru.maltsev.primemarketbackend.account.repository;

import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    List<UserAccount> findAllByUserIdAndBalanceGreaterThan(Long userId, BigDecimal balance);

    Optional<UserAccount> findByUserIdAndCurrencyCode(Long userId, String currencyCode);
}
