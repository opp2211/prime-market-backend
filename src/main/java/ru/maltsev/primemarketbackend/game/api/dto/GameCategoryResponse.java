package ru.maltsev.primemarketbackend.game.api.dto;

import ru.maltsev.primemarketbackend.game.domain.Category;
import ru.maltsev.primemarketbackend.game.domain.GameCategory;

public record GameCategoryResponse(Long categoryId,
                                   String code,
                                   String title) {
    public static GameCategoryResponse from(GameCategory gameCategory) {
        Category category = gameCategory.getCategory();
        return new GameCategoryResponse(
                category.getId(),
                category.getCode(),
                category.getTitle());
    }
}
