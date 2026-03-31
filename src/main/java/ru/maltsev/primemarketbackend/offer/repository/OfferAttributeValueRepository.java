package ru.maltsev.primemarketbackend.offer.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.offer.domain.OfferAttributeValue;

public interface OfferAttributeValueRepository extends JpaRepository<OfferAttributeValue, Long> {
    @Modifying
    @Query("delete from OfferAttributeValue oav where oav.offer.id = :offerId")
    void deleteByOfferId(@Param("offerId") Long offerId);

    @Query("""
        select oav
        from OfferAttributeValue oav
        join fetch oav.categoryAttribute a
        left join fetch oav.categoryAttributeOption ao
        where oav.offer.id = :offerId
        order by a.sortOrder asc, a.title asc
        """)
    List<OfferAttributeValue> findAllByOfferId(@Param("offerId") Long offerId);
}
