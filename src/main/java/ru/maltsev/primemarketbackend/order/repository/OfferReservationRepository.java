package ru.maltsev.primemarketbackend.order.repository;

import java.math.BigDecimal;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.order.domain.OfferReservation;

public interface OfferReservationRepository extends JpaRepository<OfferReservation, Long> {
    @Query("""
        select coalesce(sum(r.quantity), 0)
        from OfferReservation r
        where r.offerId = :offerId
          and r.status = :status
        """)
    BigDecimal sumQuantityByOfferIdAndStatus(@Param("offerId") Long offerId, @Param("status") String status);

    Optional<OfferReservation> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r
        from OfferReservation r
        where r.orderId = :orderId
        """)
    Optional<OfferReservation> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
