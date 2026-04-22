package ru.maltsev.primemarketbackend.orderquote.repository;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.orderquote.domain.OrderQuote;

public interface OrderQuoteRepository extends JpaRepository<OrderQuote, Long> {
    Optional<OrderQuote> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from OrderQuote q where q.publicId = :publicId")
    Optional<OrderQuote> findByPublicIdForUpdate(@Param("publicId") UUID publicId);
}
