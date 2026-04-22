package ru.maltsev.primemarketbackend.order.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.order.domain.OrderRequest;

public interface OrderRequestRepository extends JpaRepository<OrderRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r
        from OrderRequest r
        where r.publicId = :publicId
        """)
    Optional<OrderRequest> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    @Query("""
        select r
        from OrderRequest r
        where r.orderId = :orderId
          and r.status = :status
        order by r.createdAt desc, r.id desc
        """)
    List<OrderRequest> findAllByOrderIdAndStatusOrderByCreatedAtDesc(
        @Param("orderId") Long orderId,
        @Param("status") String status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r
        from OrderRequest r
        where r.orderId = :orderId
          and r.requestType = :requestType
          and r.status = :status
        """)
    Optional<OrderRequest> findPendingByOrderIdAndRequestTypeForUpdate(
        @Param("orderId") Long orderId,
        @Param("requestType") String requestType,
        @Param("status") String status
    );
}
