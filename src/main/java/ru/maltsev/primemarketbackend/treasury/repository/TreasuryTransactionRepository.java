package ru.maltsev.primemarketbackend.treasury.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationType;
import ru.maltsev.primemarketbackend.treasury.domain.TreasuryTransaction;

public interface TreasuryTransactionRepository extends JpaRepository<TreasuryTransaction, Long> {
    @EntityGraph(attributePaths = "treasuryAccount")
    Page<TreasuryTransaction> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = "treasuryAccount")
    Page<TreasuryTransaction> findAllByTreasuryAccountPublicIdOrderByCreatedAtDescIdDesc(
        UUID treasuryAccountPublicId,
        Pageable pageable
    );

    @EntityGraph(attributePaths = "treasuryAccount")
    List<TreasuryTransaction> findAllByOperationTypeAndOperationPublicIdOrderByCreatedAtAscIdAsc(
        MoneyOperationType operationType,
        UUID operationPublicId
    );
}
