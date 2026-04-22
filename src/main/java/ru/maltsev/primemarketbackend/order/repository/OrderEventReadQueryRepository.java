package ru.maltsev.primemarketbackend.order.repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import ru.maltsev.primemarketbackend.order.domain.OrderEvent;

@Repository
@RequiredArgsConstructor
public class OrderEventReadQueryRepository {
    private final EntityManager entityManager;

    public List<OrderEvent> findAllByOrderIdOrderedOldestFirst(Long orderId) {
        return entityManager.createQuery(
                """
                    select e
                    from OrderEvent e
                    where e.orderId = :orderId
                    order by e.createdAt asc, e.id asc
                    """,
                OrderEvent.class
            )
            .setParameter("orderId", orderId)
            .getResultList();
    }
}
