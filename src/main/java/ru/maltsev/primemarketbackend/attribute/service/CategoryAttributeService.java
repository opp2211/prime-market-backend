package ru.maltsev.primemarketbackend.attribute.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.maltsev.primemarketbackend.attribute.api.dto.CategoryAttributeOptionResponse;
import ru.maltsev.primemarketbackend.attribute.api.dto.CategoryAttributeResponse;
import ru.maltsev.primemarketbackend.attribute.domain.CategoryAttribute;
import ru.maltsev.primemarketbackend.attribute.domain.CategoryAttributeOption;
import ru.maltsev.primemarketbackend.attribute.repository.CategoryAttributeRepository;
import ru.maltsev.primemarketbackend.attribute.repository.CategoryAttributeOptionRepository;

@Service
@RequiredArgsConstructor
public class CategoryAttributeService {
    private final CategoryAttributeRepository categoryAttributeRepository;
    private final CategoryAttributeOptionRepository categoryAttributeOptionRepository;

    public List<CategoryAttributeResponse> getActiveCategoryAttributes(String gameSlug, String categorySlug) {
        List<CategoryAttribute> attributes =
            categoryAttributeRepository.findActiveByGameAndCategorySlug(gameSlug, categorySlug);
        if (attributes.isEmpty()) {
            return List.of();
        }

        List<Long> attributeIds = attributes.stream().map(CategoryAttribute::getId).toList();
        List<CategoryAttributeOption> options = categoryAttributeOptionRepository
            .findActiveByAttributeIds(attributeIds);

        Map<Long, List<CategoryAttributeOptionResponse>> optionsByAttributeId = options.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    option -> option.getCategoryAttribute().getId(),
                    java.util.stream.Collectors.mapping(
                        CategoryAttributeOptionResponse::from,
                        java.util.stream.Collectors.toList()
                    )
                )
            );

        return attributes.stream()
            .map(attribute -> CategoryAttributeResponse.from(
                attribute,
                optionsByAttributeId.getOrDefault(attribute.getId(), List.of())
            ))
            .toList();
    }
}
