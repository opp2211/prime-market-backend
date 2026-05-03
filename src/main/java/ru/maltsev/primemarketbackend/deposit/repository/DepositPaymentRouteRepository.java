package ru.maltsev.primemarketbackend.deposit.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.deposit.domain.DepositPaymentRoute;

public interface DepositPaymentRouteRepository extends JpaRepository<DepositPaymentRoute, Long> {
    @EntityGraph(attributePaths = {"depositMethod", "treasuryAccount"})
    Optional<DepositPaymentRoute> findByPublicId(UUID publicId);

    @EntityGraph(attributePaths = {"depositMethod", "treasuryAccount"})
    @Query("""
        select r from DepositPaymentRoute r
        where (:depositMethodId is null or r.depositMethod.id = :depositMethodId)
          and (:activeOnly = false or r.active = true)
        order by r.depositMethod.id asc, r.priority asc, r.id asc
        """)
    List<DepositPaymentRoute> listRoutes(
        @Param("depositMethodId") Long depositMethodId,
        @Param("activeOnly") boolean activeOnly
    );

    @EntityGraph(attributePaths = {"depositMethod", "treasuryAccount"})
    @Query("""
        select r from DepositPaymentRoute r
        where r.depositMethod.id = :depositMethodId
          and r.active = true
          and r.treasuryAccount.active = true
          and (r.minAmount is null or r.minAmount <= :amount)
          and (r.maxAmount is null or r.maxAmount >= :amount)
        order by r.priority asc, r.id asc
        """)
    List<DepositPaymentRoute> findActiveCandidates(
        @Param("depositMethodId") Long depositMethodId,
        @Param("amount") BigDecimal amount
    );
}
