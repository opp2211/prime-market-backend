package ru.maltsev.primemarketbackend.order.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.order.domain.OrderDispute;

public interface OrderDisputeRepository extends JpaRepository<OrderDispute, Long> {
    boolean existsByOrderId(Long orderId);

    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<String> statuses);

    Optional<OrderDispute> findTopByOrderIdOrderByCreatedAtDescIdDesc(Long orderId);

    Optional<OrderDispute> findTopByOrderIdAndStatusInOrderByCreatedAtDescIdDesc(
        Long orderId,
        Collection<String> statuses
    );

    List<OrderDispute> findAllByStatusInOrderByCreatedAtDescIdDesc(Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select d
        from OrderDispute d
        where d.publicId = :publicId
        """)
    Optional<OrderDispute> findByPublicIdForUpdate(@Param("publicId") UUID publicId);
}
