package ru.maltsev.primemarketbackend.attribute.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.attribute.domain.CategoryAttribute;

public interface CategoryAttributeRepository extends JpaRepository<CategoryAttribute, Long> {
    @Query("""
        select a
        from CategoryAttribute a
        join a.category c
        join c.game g
        where g.slug = :gameSlug
          and g.active = true
          and c.slug = :categorySlug
          and c.active = true
          and a.active = true
        order by a.sortOrder asc, a.title asc
        """)
    List<CategoryAttribute> findActiveByGameAndCategorySlug(
        @Param("gameSlug") String gameSlug,
        @Param("categorySlug") String categorySlug
    );

    @Query("""
        select a
        from CategoryAttribute a
        join a.category c
        where c.id = :categoryId
          and c.active = true
          and a.active = true
        order by a.sortOrder asc, a.title asc
        """)
    List<CategoryAttribute> findActiveByCategoryId(@Param("categoryId") Long categoryId);
}
