package ru.maltsev.primemarketbackend.withdrawal.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalMethod;

public interface WithdrawalMethodRepository extends JpaRepository<WithdrawalMethod, Long> {
    List<WithdrawalMethod> findAllByCurrencyCodeIgnoreCaseAndActiveTrueOrderByIdAsc(String currencyCode);

    Optional<WithdrawalMethod> findByIdAndActiveTrue(Long id);
}
