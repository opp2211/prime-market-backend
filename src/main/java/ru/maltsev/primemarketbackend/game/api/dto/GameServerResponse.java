package ru.maltsev.primemarketbackend.game.api.dto;

import ru.maltsev.primemarketbackend.game.domain.GameServer;

public record GameServerResponse(long id, String name, String slug) {

    public static GameServerResponse from(GameServer gameServer) {
        return new GameServerResponse(gameServer.getId(), gameServer.getName(), gameServer.getSlug());
    }
}
