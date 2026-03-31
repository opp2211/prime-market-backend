package ru.maltsev.primemarketbackend.offer.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.offer.domain.OfferContextValue;

public interface OfferContextValueRepository extends JpaRepository<OfferContextValue, Long> {
    @Modifying
    @Query("delete from OfferContextValue ocv where ocv.offer.id = :offerId")
    void deleteByOfferId(@Param("offerId") Long offerId);

    @Query("""
        select ocv
        from OfferContextValue ocv
        join fetch ocv.contextDimension cd
        join fetch ocv.contextDimensionValue cv
        where ocv.offer.id = :offerId
        order by cd.sortOrder asc, cd.title asc
        """)
    List<OfferContextValue> findAllByOfferId(@Param("offerId") Long offerId);
}
