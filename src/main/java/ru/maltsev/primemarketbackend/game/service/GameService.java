package ru.maltsev.primemarketbackend.game.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.maltsev.primemarketbackend.config.AssetsProperties;
import ru.maltsev.primemarketbackend.game.api.dto.GameCategoryResponse;
import ru.maltsev.primemarketbackend.game.api.dto.GameResponse;
import ru.maltsev.primemarketbackend.game.api.dto.GameServerResponse;
import ru.maltsev.primemarketbackend.game.api.dto.ProductResponse;
import ru.maltsev.primemarketbackend.game.repository.GameCategoryRepository;
import ru.maltsev.primemarketbackend.game.repository.GameRepository;
import ru.maltsev.primemarketbackend.game.repository.GameServerRepository;
import ru.maltsev.primemarketbackend.game.repository.ProductRepository;

@Service
@RequiredArgsConstructor
public class GameService {
    private final GameRepository gameRepository;
    private final GameServerRepository gameServerRepository;
    private final GameCategoryRepository gameCategoryRepository;
    private final ProductRepository productRepository;
    private final AssetsProperties assetsProperties;

    public List<GameResponse> getActiveGames() {
        String baseUrl = assetsProperties.baseUrl();
        return gameRepository.findAllByActiveTrueOrderBySortOrderAsc()
            .stream()
            .map(game -> GameResponse.from(game, baseUrl))
            .toList();
    }

    public List<GameServerResponse> getActiveGameServersByGameId(Long gameId) {
        return gameServerRepository.findAllByGame_IdAndActiveTrueOrderBySortOrderAsc(gameId)
                .stream()
                .map(GameServerResponse::from)
                .toList();
    }

    public List<GameCategoryResponse> getActiveGameCategoriesByGameId(Long gameId) {
        return gameCategoryRepository.findAllByGame_IdAndActiveTrueOrderBySortOrderAsc(gameId)
                .stream()
                .map(GameCategoryResponse::from)
                .toList();
    }

    public List<ProductResponse> getActiveProductsByGameAndCategory(Long gameId, Long categoryId) {
        return productRepository.findAllByGame_IdAndCategory_IdAndActiveTrue(gameId, categoryId)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }


}
