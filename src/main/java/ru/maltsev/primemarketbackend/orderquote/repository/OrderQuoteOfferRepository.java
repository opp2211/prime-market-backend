package ru.maltsev.primemarketbackend.orderquote.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.offer.domain.Offer;

public interface OrderQuoteOfferRepository extends JpaRepository<Offer, Long> {
    @Query("""
        select new ru.maltsev.primemarketbackend.orderquote.repository.OrderQuoteOfferProjection(
            o.id,
            o.version,
            o.userId,
            u.username,
            u.active,
            o.gameId,
            g.slug,
            g.title,
            g.active,
            o.categoryId,
            c.slug,
            c.title,
            c.active,
            o.side,
            o.status,
            o.title,
            o.description,
            o.tradeTerms,
            o.priceCurrencyCode,
            o.priceAmount,
            o.quantity,
            o.minTradeQuantity,
            o.maxTradeQuantity,
            o.quantityStep,
            o.publishedAt
        )
        from Offer o
        join ru.maltsev.primemarketbackend.category.domain.Category c on c.id = o.categoryId
        join c.game g
        join ru.maltsev.primemarketbackend.user.domain.User u on u.id = o.userId
        where o.id = :offerId
        """)
    Optional<OrderQuoteOfferProjection> findProjectionByOfferId(@Param("offerId") Long offerId);
}
