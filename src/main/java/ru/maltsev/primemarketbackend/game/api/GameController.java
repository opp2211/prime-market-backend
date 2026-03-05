package ru.maltsev.primemarketbackend.game.api;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.game.api.dto.GameCategoryResponse;
import ru.maltsev.primemarketbackend.game.api.dto.GameResponse;
import ru.maltsev.primemarketbackend.game.api.dto.GameServerResponse;
import ru.maltsev.primemarketbackend.game.api.dto.ProductResponse;
import ru.maltsev.primemarketbackend.game.service.GameService;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {
    private final GameService gameService;

    @GetMapping
    public ResponseEntity<List<GameResponse>> getActiveGames() {
        List<GameResponse> response = gameService.getActiveGames();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{gameId}/servers")
    public ResponseEntity<List<GameServerResponse>> getServers(@PathVariable Long gameId) {
        List<GameServerResponse> response = gameService.getActiveGameServersByGameId(gameId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{gameId}/categories")
    public ResponseEntity<List<GameCategoryResponse>> getGameCategories(@PathVariable Long gameId) {
        List<GameCategoryResponse> response = gameService.getActiveGameCategoriesByGameId(gameId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{gameId}/categories/{categoryId}/products")
    public ResponseEntity<List<ProductResponse>> getActiveProducts(@PathVariable Long gameId,
                                                                    @PathVariable Long categoryId) {
        List<ProductResponse> response = gameService.getActiveProductsByGameAndCategory(gameId, categoryId);
        return ResponseEntity.ok(response);
    }

}
