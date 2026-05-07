package ru.maltsev.primemarketbackend.deposit.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.deposit.domain.DepositPaymentInstruction;

public interface DepositPaymentInstructionRepository extends JpaRepository<DepositPaymentInstruction, Long> {
    @EntityGraph(attributePaths = {"depositPaymentRoute", "treasuryAccount"})
    Optional<DepositPaymentInstruction> findByDepositRequestPublicCode(String depositRequestPublicCode);

    @EntityGraph(attributePaths = {"depositPaymentRoute", "treasuryAccount"})
    Optional<DepositPaymentInstruction> findByDepositRequestId(Long depositRequestId);
}
