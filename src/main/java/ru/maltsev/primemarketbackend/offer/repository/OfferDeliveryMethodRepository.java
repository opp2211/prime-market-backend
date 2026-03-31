package ru.maltsev.primemarketbackend.offer.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.offer.domain.OfferDeliveryMethod;
import ru.maltsev.primemarketbackend.offer.domain.OfferDeliveryMethodId;

public interface OfferDeliveryMethodRepository extends JpaRepository<OfferDeliveryMethod, OfferDeliveryMethodId> {
    @Modifying
    @Query("delete from OfferDeliveryMethod odm where odm.offer.id = :offerId")
    void deleteByOfferId(@Param("offerId") Long offerId);

    @Query("""
        select odm
        from OfferDeliveryMethod odm
        join fetch odm.deliveryMethod dm
        where odm.offer.id = :offerId
        order by dm.sortOrder asc, dm.title asc
        """)
    List<OfferDeliveryMethod> findAllByOfferId(@Param("offerId") Long offerId);
}
