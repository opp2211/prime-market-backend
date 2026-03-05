package ru.maltsev.primemarketbackend.currency.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.currency.domain.FxRate;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {
}
