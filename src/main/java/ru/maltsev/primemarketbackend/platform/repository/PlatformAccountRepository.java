package ru.maltsev.primemarketbackend.platform.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import ru.maltsev.primemarketbackend.platform.domain.PlatformAccount;

public interface PlatformAccountRepository extends JpaRepository<PlatformAccount, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PlatformAccount> findByCurrencyCodeIgnoreCase(String currencyCode);
}
