package ru.maltsev.primemarketbackend.currency.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.currency.domain.CurrencyRate;

public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Long> {
    Optional<CurrencyRate> findByFromCurrencyCodeIgnoreCaseAndToCurrencyCodeIgnoreCase(
        String fromCurrencyCode,
        String toCurrencyCode
    );
}
