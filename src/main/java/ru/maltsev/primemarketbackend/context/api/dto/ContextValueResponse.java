package ru.maltsev.primemarketbackend.context.api.dto;

import ru.maltsev.primemarketbackend.context.domain.ContextDimensionValue;

public record ContextValueResponse(String slug, String title) {
    public static ContextValueResponse from(ContextDimensionValue value) {
        String slug = value.getSlug();
        String title = value.getTitle();
        if (title == null || title.isBlank()) {
            title = slug;
        }
        return new ContextValueResponse(slug, title);
    }
}
