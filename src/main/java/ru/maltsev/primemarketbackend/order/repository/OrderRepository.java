package ru.maltsev.primemarketbackend.order.repository;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.order.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.publicId = :publicId")
    Optional<Order> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select o
        from Order o
        where o.status = 'pending'
          and o.expiresAt <= :now
        order by o.id asc
        """)
    List<Order> findAllPendingExpiredForUpdate(@Param("now") Instant now);
}
