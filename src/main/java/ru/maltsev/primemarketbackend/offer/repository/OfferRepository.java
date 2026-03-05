package ru.maltsev.primemarketbackend.offer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.offer.domain.Offer;
import ru.maltsev.primemarketbackend.offer.domain.OfferSide;
import ru.maltsev.primemarketbackend.offer.domain.OfferStatus;

import java.util.List;

public interface OfferRepository extends JpaRepository<Offer, Long> {
    List<Offer> findOfferByProductIdAndServerIdAndStatusAndSide(
        Long productId,
        Long serverId,
        OfferStatus status,
        OfferSide side
    );
}
