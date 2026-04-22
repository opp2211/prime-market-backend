package ru.maltsev.primemarketbackend.offer.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.offer.domain.Offer;

public interface OfferRepository extends JpaRepository<Offer, Long> {
    Optional<Offer> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Offer o where o.id = :offerId and o.userId = :userId")
    Optional<Offer> findByIdAndUserIdForUpdate(@Param("offerId") Long offerId, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Offer o where o.id = :offerId")
    Optional<Offer> findByIdForUpdate(@Param("offerId") Long offerId);

    List<Offer> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
        select new ru.maltsev.primemarketbackend.offer.repository.OfferView(
            o.id,
            o.gameId,
            g.slug,
            g.title,
            o.categoryId,
            c.slug,
            c.title,
            o.side,
            o.title,
            o.description,
            o.tradeTerms,
            o.priceCurrencyCode,
            o.priceAmount,
            o.quantity,
            o.minTradeQuantity,
            o.maxTradeQuantity,
            o.quantityStep,
            o.status,
            o.createdAt,
            o.updatedAt,
            o.publishedAt
        )
        from Offer o
        join ru.maltsev.primemarketbackend.category.domain.Category c on c.id = o.categoryId
        join c.game g
        where o.userId = :userId
        order by o.createdAt desc
        """)
    List<OfferView> findViewsByUserId(@Param("userId") Long userId);

    @Query("""
        select new ru.maltsev.primemarketbackend.offer.repository.OfferView(
            o.id,
            o.gameId,
            g.slug,
            g.title,
            o.categoryId,
            c.slug,
            c.title,
            o.side,
            o.title,
            o.description,
            o.tradeTerms,
            o.priceCurrencyCode,
            o.priceAmount,
            o.quantity,
            o.minTradeQuantity,
            o.maxTradeQuantity,
            o.quantityStep,
            o.status,
            o.createdAt,
            o.updatedAt,
            o.publishedAt
        )
        from Offer o
        join ru.maltsev.primemarketbackend.category.domain.Category c on c.id = o.categoryId
        join c.game g
        where o.id = :offerId
          and o.userId = :userId
        """)
    Optional<OfferView> findViewByIdAndUserId(@Param("offerId") Long offerId, @Param("userId") Long userId);
}
