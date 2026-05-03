package ru.maltsev.primemarketbackend.treasury.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryAccount;

public interface TreasuryAccountRepository extends JpaRepository<TreasuryAccount, Long> {
    List<TreasuryAccount> findAllByOrderByCurrencyCodeAscTitleAscIdAsc();

    List<TreasuryAccount> findAllByActiveTrueOrderByCurrencyCodeAscTitleAscIdAsc();

    Optional<TreasuryAccount> findByPublicId(UUID publicId);

    boolean existsByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from TreasuryAccount account where account.publicId = :publicId")
    Optional<TreasuryAccount> findByPublicIdForUpdate(@Param("publicId") UUID publicId);
}
