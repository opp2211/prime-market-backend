package ru.maltsev.primemarketbackend.currency.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.currency.domain.Currency;

public interface CurrencyRepository extends JpaRepository<Currency, String> {
    List<Currency> findAllByActiveTrueOrderByCodeAsc();

    boolean existsByCodeIgnoreCaseAndActiveTrue(String code);
}
