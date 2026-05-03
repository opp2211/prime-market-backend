package ru.maltsev.primemarketbackend.withdrawal.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalPayoutPlan;

public interface WithdrawalPayoutPlanRepository extends JpaRepository<WithdrawalPayoutPlan, Long> {
    @EntityGraph(attributePaths = "treasuryAccount")
    Optional<WithdrawalPayoutPlan> findByWithdrawalRequestId(Long withdrawalRequestId);
}
