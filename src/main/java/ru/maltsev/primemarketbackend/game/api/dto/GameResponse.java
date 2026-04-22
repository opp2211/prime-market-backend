package ru.maltsev.primemarketbackend.game.api.dto;

import ru.maltsev.primemarketbackend.game.domain.Game;

public record GameResponse(Long id, String slug, String title) {
    public static GameResponse from(Game game) {
        return new GameResponse(game.getId(), game.getSlug(), game.getTitle());
    }
}
