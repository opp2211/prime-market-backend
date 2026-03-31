package ru.maltsev.primemarketbackend.attribute.api;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.attribute.api.dto.CategoryAttributeResponse;
import ru.maltsev.primemarketbackend.attribute.service.CategoryAttributeService;

@RestController
@RequestMapping("/api/games/{gameSlug}/categories/{categorySlug}/attributes")
@RequiredArgsConstructor
public class CategoryAttributeController {
    private final CategoryAttributeService categoryAttributeService;

    @GetMapping
    public ResponseEntity<List<CategoryAttributeResponse>> getCategoryAttributes(
        @PathVariable String gameSlug,
        @PathVariable String categorySlug
    ) {
        List<CategoryAttributeResponse> response =
            categoryAttributeService.getActiveCategoryAttributes(gameSlug, categorySlug);
        return ResponseEntity.ok(response);
    }
}
