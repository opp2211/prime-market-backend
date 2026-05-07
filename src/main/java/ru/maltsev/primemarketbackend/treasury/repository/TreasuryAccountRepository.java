package ru.maltsev.primemarketbackend.treasury.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryAccount;

public interface TreasuryAccountRepository extends JpaRepository<TreasuryAccount, Long> {
    List<TreasuryAccount> findAllByOrderByCurrencyCodeAscTitleAscIdAsc();

    List<TreasuryAccount> findAllByActiveTrueOrderByCurrencyCodeAscTitleAscIdAsc();

    boolean existsByCodeIgnoreCase(String code);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from TreasuryAccount account where account.id = :id")
    Optional<TreasuryAccount> findById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from TreasuryAccount account where account.id = :id")
    Optional<TreasuryAccount> findByIdForUpdate(@Param("id") Long id);
}
