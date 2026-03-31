package ru.maltsev.primemarketbackend.category.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.category.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    @Query("""
        select c
        from Category c
        join c.game g
        where g.slug = :gameSlug
          and g.active = true
          and c.active = true
        order by c.sortOrder asc, c.title asc
        """)
    List<Category> findActiveByGameSlug(@Param("gameSlug") String gameSlug);

    @Query("""
        select c
        from Category c
        join c.game g
        where c.id = :categoryId
          and g.id = :gameId
          and c.active = true
          and g.active = true
        """)
    Optional<Category> findActiveByIdAndGameId(@Param("categoryId") Long categoryId, @Param("gameId") Long gameId);

    @Query("""
        select c
        from Category c
        join fetch c.game g
        where lower(g.slug) = lower(:gameSlug)
          and lower(c.slug) = lower(:categorySlug)
          and g.active = true
          and c.active = true
        """)
    Optional<Category> findActiveByGameSlugAndCategorySlug(
        @Param("gameSlug") String gameSlug,
        @Param("categorySlug") String categorySlug
    );
}
