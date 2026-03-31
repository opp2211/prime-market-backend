package ru.maltsev.primemarketbackend.attribute.api.dto;

import ru.maltsev.primemarketbackend.attribute.domain.CategoryAttributeOption;

public record CategoryAttributeOptionResponse(String slug, String title) {
    public static CategoryAttributeOptionResponse from(CategoryAttributeOption option) {
        String slug = option.getSlug();
        String title = option.getTitle();
        if (title == null || title.isBlank()) {
            title = slug;
        }
        return new CategoryAttributeOptionResponse(slug, title);
    }
}
