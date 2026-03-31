package ru.maltsev.primemarketbackend.tradefield.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.tradefield.domain.CategoryTradeFieldConfig;

public interface CategoryTradeFieldConfigRepository extends JpaRepository<CategoryTradeFieldConfig, Long> {
    @Query("""
        select cfg
        from CategoryTradeFieldConfig cfg
        join cfg.category c
        join c.game g
        where g.slug = :gameSlug
          and g.active = true
          and c.slug = :categorySlug
          and c.active = true
        order by cfg.sortOrder asc, cfg.id asc
        """)
    List<CategoryTradeFieldConfig> findByGameAndCategorySlug(
        @Param("gameSlug") String gameSlug,
        @Param("categorySlug") String categorySlug
    );

    @Query("""
        select cfg
        from CategoryTradeFieldConfig cfg
        where cfg.category.id = :categoryId
        order by cfg.sortOrder asc, cfg.id asc
        """)
    List<CategoryTradeFieldConfig> findByCategoryId(@Param("categoryId") Long categoryId);
}
