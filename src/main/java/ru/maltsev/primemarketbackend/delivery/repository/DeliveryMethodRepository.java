package ru.maltsev.primemarketbackend.delivery.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.delivery.domain.DeliveryMethod;

public interface DeliveryMethodRepository extends JpaRepository<DeliveryMethod, Long> {
    @Query("""
        select dm
        from DeliveryMethod dm
        join dm.category c
        where c.id = :categoryId
          and c.active = true
          and dm.active = true
          and dm.slug in :slugs
        """)
    List<DeliveryMethod> findActiveByCategoryIdAndSlugIn(
        @Param("categoryId") Long categoryId,
        @Param("slugs") List<String> slugs
    );

    @Query("""
        select dm
        from DeliveryMethod dm
        join dm.category c
        join c.game g
        where g.slug = :gameSlug
          and g.active = true
          and c.slug = :categorySlug
          and c.active = true
          and dm.active = true
        order by dm.sortOrder asc, dm.title asc
        """)
    List<DeliveryMethod> findActiveByGameAndCategorySlug(
        @Param("gameSlug") String gameSlug,
        @Param("categorySlug") String categorySlug
    );
}
