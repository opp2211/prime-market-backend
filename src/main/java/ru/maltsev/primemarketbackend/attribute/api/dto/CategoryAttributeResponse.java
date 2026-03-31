package ru.maltsev.primemarketbackend.attribute.api.dto;

import ru.maltsev.primemarketbackend.attribute.domain.CategoryAttribute;

import java.util.List;

public record CategoryAttributeResponse(
    String slug,
    String title,
    String dataType,
    boolean isRequired,
    List<CategoryAttributeOptionResponse> options
) {
    public static CategoryAttributeResponse from(
        CategoryAttribute attribute,
        List<CategoryAttributeOptionResponse> options
    ) {
        String slug = attribute.getSlug();
        String title = attribute.getTitle();
        if (title == null || title.isBlank()) {
            title = slug;
        }
        return new CategoryAttributeResponse(
            slug,
            title,
            attribute.getDataType(),
            attribute.isRequired(),
            options
        );
    }
}
