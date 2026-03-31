package ru.maltsev.primemarketbackend.category.api;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.category.api.dto.CategoryResponse;
import ru.maltsev.primemarketbackend.category.service.CategoryService;

@RestController
@RequestMapping("/api/games/{gameSlug}/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getActiveCategories(@PathVariable String gameSlug) {
        List<CategoryResponse> response = categoryService.getActiveCategoriesByGameSlug(gameSlug);
        return ResponseEntity.ok(response);
    }
}
