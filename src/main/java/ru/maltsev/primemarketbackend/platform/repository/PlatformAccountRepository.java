package ru.maltsev.primemarketbackend.platform.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccount;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccountCode;

public interface PlatformAccountRepository extends JpaRepository<PlatformAccount, Long> {
    List<PlatformAccount> findAllByOrderByCurrencyCodeAscAccountCodeAscIdAsc();

    Optional<PlatformAccount> findByAccountCodeAndCurrencyCodeIgnoreCase(
        PlatformAccountCode accountCode,
        String currencyCode
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select pa from PlatformAccount pa
        where pa.accountCode = ru.maltsev.primemarketbackend.platform.domain.PlatformAccountCode.FEES
          and upper(pa.currencyCode) = upper(:currencyCode)
        """)
    Optional<PlatformAccount> findByCurrencyCodeIgnoreCase(@Param("currencyCode") String currencyCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select pa from PlatformAccount pa
        where pa.accountCode = :accountCode
          and upper(pa.currencyCode) = upper(:currencyCode)
        """)
    Optional<PlatformAccount> findByAccountCodeAndCurrencyCodeForUpdate(
        @Param("accountCode") PlatformAccountCode accountCode,
        @Param("currencyCode") String currencyCode
    );

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PlatformAccount> findById(Long id);
}
