package ru.maltsev.primemarketbackend.account.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    List<UserAccount> findAllByUserId(Long userId);

    Optional<UserAccount> findByUserIdAndCurrencyCode(Long userId, String currencyCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserAccount> findByUserIdAndCurrencyCodeIgnoreCase(Long userId, String currencyCode);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserAccount> findById(Long id);

    @Modifying
    @Query("""
        update UserAccount ua
        set ua.reserved = ua.reserved - :amount
        where ua.id = :userAccountId
          and ua.reserved >= :amount
        """)
    int decreaseReserved(@Param("userAccountId") Long userAccountId, @Param("amount") BigDecimal amount);
}
