package ru.maltsev.primemarketbackend.deposit.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.deposit.domain.DepositMethod;

public interface DepositMethodRepository extends JpaRepository<DepositMethod, Long> {
    List<DepositMethod> findAllByCurrencyCodeAndActiveTrueOrderByIdAsc(String currencyCode);
}
