package ru.maltsev.primemarketbackend.game.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.game.api.dto.GameResponse;
import ru.maltsev.primemarketbackend.game.domain.Game;
import ru.maltsev.primemarketbackend.game.repository.GameRepository;

@Service
@RequiredArgsConstructor
public class GameService {
    private final GameRepository gameRepository;

    public List<GameResponse> getActiveGames() {
        return gameRepository.findAllByActiveTrueOrderBySortOrderAsc()
            .stream()
            .map(GameResponse::from)
            .toList();
    }

    public Game requireActiveGame(String gameSlug) {
        return gameRepository.findBySlugIgnoreCaseAndActiveTrue(gameSlug)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "GAME_NOT_FOUND",
                "Game not found"
            ));
    }
}
