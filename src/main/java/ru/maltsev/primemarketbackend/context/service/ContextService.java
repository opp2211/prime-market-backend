package ru.maltsev.primemarketbackend.context.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.maltsev.primemarketbackend.context.api.dto.ContextResponse;
import ru.maltsev.primemarketbackend.context.api.dto.ContextValueResponse;
import ru.maltsev.primemarketbackend.context.domain.CategoryContextDimension;
import ru.maltsev.primemarketbackend.context.domain.ContextDimensionValue;
import ru.maltsev.primemarketbackend.context.repository.CategoryContextDimensionRepository;
import ru.maltsev.primemarketbackend.context.repository.ContextDimensionValueRepository;

@Service
@RequiredArgsConstructor
public class ContextService {
    private final CategoryContextDimensionRepository categoryContextDimensionRepository;
    private final ContextDimensionValueRepository contextDimensionValueRepository;

    public List<ContextResponse> getCategoryContexts(String gameSlug, String categorySlug) {
        List<CategoryContextDimension> links = categoryContextDimensionRepository
            .findActiveByGameAndCategorySlug(gameSlug, categorySlug);
        if (links.isEmpty()) {
            return List.of();
        }

        List<Long> dimensionIds = links.stream()
            .map(link -> link.getContextDimension().getId())
            .distinct()
            .toList();

        List<ContextDimensionValue> values = contextDimensionValueRepository
            .findActiveByDimensionIds(dimensionIds);

        Map<Long, List<ContextValueResponse>> optionsByDimensionId = new HashMap<>();
        for (ContextDimensionValue value : values) {
            optionsByDimensionId
                .computeIfAbsent(value.getContextDimension().getId(), key -> new ArrayList<>())
                .add(ContextValueResponse.from(value));
        }

        return links.stream()
            .map(link -> ContextResponse.from(
                link,
                optionsByDimensionId.getOrDefault(link.getContextDimension().getId(), List.of())
            ))
            .toList();
    }
}
