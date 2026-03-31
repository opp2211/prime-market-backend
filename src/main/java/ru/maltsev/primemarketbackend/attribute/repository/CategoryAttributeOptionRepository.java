package ru.maltsev.primemarketbackend.attribute.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.attribute.domain.CategoryAttributeOption;

public interface CategoryAttributeOptionRepository extends JpaRepository<CategoryAttributeOption, Long> {
    @Query("""
        select o
        from CategoryAttributeOption o
        where o.categoryAttribute.id in :attributeIds
          and o.active = true
        order by o.categoryAttribute.id asc, o.sortOrder asc, o.title asc
        """)
    List<CategoryAttributeOption> findActiveByAttributeIds(@Param("attributeIds") List<Long> attributeIds);
}
