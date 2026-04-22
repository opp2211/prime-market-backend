package ru.maltsev.primemarketbackend.order.repository;

import java.math.BigDecimal;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.order.domain.UserAccountHoldAllocation;

public interface UserAccountHoldAllocationRepository extends JpaRepository<UserAccountHoldAllocation, Long> {
    @Query("""
        select coalesce(sum(a.amount), 0)
        from UserAccountHoldAllocation a
        where a.userAccountHoldId = :userAccountHoldId
          and a.status = :status
        """)
    BigDecimal sumAmountByUserAccountHoldIdAndStatus(
        @Param("userAccountHoldId") Long userAccountHoldId,
        @Param("status") String status
    );

    Optional<UserAccountHoldAllocation> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a
        from UserAccountHoldAllocation a
        where a.orderId = :orderId
        """)
    Optional<UserAccountHoldAllocation> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
