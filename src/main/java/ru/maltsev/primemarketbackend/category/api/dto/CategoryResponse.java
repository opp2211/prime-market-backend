package ru.maltsev.primemarketbackend.category.api.dto;

import ru.maltsev.primemarketbackend.category.domain.Category;

public record CategoryResponse(Long id, String slug, String title) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getSlug(), category.getTitle());
    }
}
