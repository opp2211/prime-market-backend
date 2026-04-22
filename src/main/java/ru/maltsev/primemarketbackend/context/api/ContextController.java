package ru.maltsev.primemarketbackend.context.api;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.context.api.dto.ContextResponse;
import ru.maltsev.primemarketbackend.context.service.ContextService;

@RestController
@RequestMapping("/api/games/{gameSlug}/categories/{categorySlug}/contexts")
@RequiredArgsConstructor
public class ContextController {
    private final ContextService contextService;

    @GetMapping
    public ResponseEntity<List<ContextResponse>> getCategoryContexts(
        @PathVariable String gameSlug,
        @PathVariable String categorySlug
    ) {
        List<ContextResponse> response = contextService.getCategoryContexts(gameSlug, categorySlug);
        return ResponseEntity.ok(response);
    }
}
