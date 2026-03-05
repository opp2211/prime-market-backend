package ru.maltsev.primemarketbackend.offer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.offer.domain.Offer;

import java.util.List;

public interface OfferRepository extends JpaRepository<Offer, Long> {
    List<Offer> findOfferByProductIdAndServerIdAndStatusAndSide(Long productId, Long serverId, String status, String side);
}
