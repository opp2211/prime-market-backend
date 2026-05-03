package ru.maltsev.primemarketbackend.money.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationEvent;
import ru.maltsev.primemarketbackend.money.domain.MoneyOperationType;

public interface MoneyOperationEventRepository extends JpaRepository<MoneyOperationEvent, Long> {
    List<MoneyOperationEvent> findAllByOperationTypeAndOperationPublicIdOrderByCreatedAtAscIdAsc(
        MoneyOperationType operationType,
        UUID operationPublicId
    );
}
