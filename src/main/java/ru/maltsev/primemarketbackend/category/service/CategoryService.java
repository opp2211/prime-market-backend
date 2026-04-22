package ru.maltsev.primemarketbackend.category.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.maltsev.primemarketbackend.category.api.dto.CategoryResponse;
import ru.maltsev.primemarketbackend.category.repository.CategoryRepository;
import ru.maltsev.primemarketbackend.game.service.GameService;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final GameService gameService;

    public List<CategoryResponse> getActiveCategoriesByGameSlug(String gameSlug) {
        gameService.requireActiveGame(gameSlug);
        return categoryRepository.findActiveByGameSlug(gameSlug)
            .stream()
            .map(CategoryResponse::from)
            .toList();
    }
}
