package ru.maltsev.primemarketbackend.offer.api;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferResponse;
import ru.maltsev.primemarketbackend.offer.service.OfferService;

@RestController
@RequestMapping("/api/offers")
@RequiredArgsConstructor
public class OfferController {
    private final OfferService offerService;

    @GetMapping
    public ResponseEntity<List<OfferResponse>> list(
        @RequestParam Long productId,
        @RequestParam Long serverId,
        @RequestParam String side,
        @RequestParam String currencyCode
    ) {
        List<OfferResponse> response = offerService.listOffers(productId, serverId, side, currencyCode);
        return ResponseEntity.ok(response);
    }
}
