package ru.maltsev.primemarketbackend.game.api.dto;

import ru.maltsev.primemarketbackend.game.domain.Game;

public record GameResponse(String name, String slug, String logoUrl) {
    public static GameResponse from(Game game, String baseUrl) {
        return new GameResponse(game.getName(), game.getSlug(), buildLogoUrl(baseUrl, game.getLogoKey()));
    }

    private static String buildLogoUrl(String baseUrl, String logoKey) {
        if (logoKey == null || logoKey.isBlank()) {
            return null;
        }

        String normalizedKey = logoKey.startsWith("/") ? logoKey.substring(1) : logoKey;
        if (baseUrl == null || baseUrl.isBlank()) {
            return "/" + normalizedKey;
        }

        String normalizedBase = baseUrl.endsWith("/")
            ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
        return normalizedBase + "/" + normalizedKey;
    }
}
