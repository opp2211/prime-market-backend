package ru.maltsev.primemarketbackend.currency.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.currency.domain.FxRate;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {
    Optional<FxRate> findFirstByBaseCodeAndQuoteCodeAndValidToGreaterThanEqualOrderByValidToDesc(
        String baseCode,
        String quoteCode,
        Instant validTo
    );
}
