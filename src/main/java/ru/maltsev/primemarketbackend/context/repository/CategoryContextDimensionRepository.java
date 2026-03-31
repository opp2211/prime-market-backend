package ru.maltsev.primemarketbackend.context.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.context.domain.CategoryContextDimension;

public interface CategoryContextDimensionRepository extends JpaRepository<CategoryContextDimension, Long> {
    @Query("""
        select ccd
        from CategoryContextDimension ccd
        join fetch ccd.contextDimension cd
        join ccd.category c
        join c.game g
        left join fetch ccd.defaultValue dv
        where g.slug = :gameSlug
          and g.active = true
          and c.slug = :categorySlug
          and c.active = true
          and cd.active = true
        order by cd.sortOrder asc, cd.title asc
        """)
    List<CategoryContextDimension> findActiveByGameAndCategorySlug(
        @Param("gameSlug") String gameSlug,
        @Param("categorySlug") String categorySlug
    );

    @Query("""
        select ccd
        from CategoryContextDimension ccd
        join fetch ccd.contextDimension cd
        where ccd.category.id = :categoryId
          and cd.active = true
        """)
    List<CategoryContextDimension> findActiveByCategoryId(@Param("categoryId") Long categoryId);
}
