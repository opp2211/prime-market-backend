package ru.maltsev.primemarketbackend.offer.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferSchemaResponse;
import ru.maltsev.primemarketbackend.offer.service.OfferSchemaService;

@RestController
@RequestMapping("/api/games/{gameSlug}/categories/{categorySlug}/offer-schema")
@RequiredArgsConstructor
public class OfferSchemaController {
    private final OfferSchemaService offerSchemaService;

    @GetMapping
    public ResponseEntity<OfferSchemaResponse> getOfferSchema(
        @PathVariable String gameSlug,
        @PathVariable String categorySlug
    ) {
        OfferSchemaResponse response = offerSchemaService.getOfferSchema(gameSlug, categorySlug);
        return ResponseEntity.ok(response);
    }
}
