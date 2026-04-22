package ru.maltsev.primemarketbackend.context.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.context.domain.ContextDimensionValue;

public interface ContextDimensionValueRepository extends JpaRepository<ContextDimensionValue, Long> {
    @Query("""
        select v
        from ContextDimensionValue v
        where v.contextDimension.id in :dimensionIds
          and v.active = true
        order by v.contextDimension.id asc, v.sortOrder asc, v.title asc
        """)
    List<ContextDimensionValue> findActiveByDimensionIds(@Param("dimensionIds") List<Long> dimensionIds);
}
