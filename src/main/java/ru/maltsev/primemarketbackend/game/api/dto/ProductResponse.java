package ru.maltsev.primemarketbackend.game.api.dto;

import ru.maltsev.primemarketbackend.game.domain.Product;

public record ProductResponse(long id, String title, String slug, String unitCode) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getTitle(),
            product.getSlug(),
            product.getUnitCode()
        );
    }
}
