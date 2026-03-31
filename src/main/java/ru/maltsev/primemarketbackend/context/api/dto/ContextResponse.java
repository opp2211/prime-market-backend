package ru.maltsev.primemarketbackend.context.api.dto;

import java.util.List;
import ru.maltsev.primemarketbackend.context.domain.CategoryContextDimension;
import ru.maltsev.primemarketbackend.context.domain.ContextDimension;
import ru.maltsev.primemarketbackend.context.domain.ContextDimensionValue;

public record ContextResponse(
    String slug,
    String title,
    boolean isRequired,
    ContextValueResponse defaultValue,
    List<ContextValueResponse> options
) {
    public static ContextResponse from(
        CategoryContextDimension link,
        List<ContextValueResponse> options
    ) {
        ContextDimension dimension = link.getContextDimension();
        ContextDimensionValue defaultValue = link.getDefaultValue();
        ContextValueResponse defaultResponse = null;
        if (defaultValue != null && defaultValue.isActive()) {
            defaultResponse = ContextValueResponse.from(defaultValue);
        }
        String slug = dimension.getSlug();
        String title = dimension.getTitle();
        if (title == null || title.isBlank()) {
            title = slug;
        }
        return new ContextResponse(slug, title, link.isRequired(), defaultResponse, options);
    }
}
