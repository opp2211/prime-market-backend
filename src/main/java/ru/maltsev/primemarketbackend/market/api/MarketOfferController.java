package ru.maltsev.primemarketbackend.market.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.maltsev.primemarketbackend.market.api.dto.MarketOfferDetailsResponse;
import ru.maltsev.primemarketbackend.market.api.dto.MarketOfferListRequest;
import ru.maltsev.primemarketbackend.market.api.dto.MarketOfferListResponse;
import ru.maltsev.primemarketbackend.market.service.MarketOfferService;

@RestController
@RequestMapping("/api/market/offers")
@RequiredArgsConstructor
public class MarketOfferController {
    private final MarketOfferService marketOfferService;

    @GetMapping
    public ResponseEntity<MarketOfferListResponse> listOffers(
        @RequestParam(name = "gameSlug", required = false) String gameSlug,
        @RequestParam(name = "categorySlug", required = false) String categorySlug,
        @RequestParam(name = "intent", required = false) String intent,
        @RequestParam(name = "viewerCurrencyCode", required = false) String viewerCurrencyCode,
        @RequestParam(name = "platform", required = false) String platform,
        @RequestParam(name = "league", required = false) String league,
        @RequestParam(name = "mode", required = false) String mode,
        @RequestParam(name = "ruthless", required = false) String ruthless,
        @RequestParam(name = "currencyType", required = false) String currencyType,
        @RequestParam(name = "page", required = false) Integer page,
        @RequestParam(name = "size", required = false) Integer size,
        @RequestParam(name = "sort", required = false) String sort
    ) {
        MarketOfferListResponse response = marketOfferService.listOffers(new MarketOfferListRequest(
            gameSlug,
            categorySlug,
            intent,
            viewerCurrencyCode,
            platform,
            league,
            mode,
            ruthless,
            currencyType,
            page,
            size,
            sort
        ));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{offerId}")
    public ResponseEntity<MarketOfferDetailsResponse> getOffer(
        @PathVariable Long offerId,
        @RequestParam(name = "intent", required = false) String intent,
        @RequestParam(name = "viewerCurrencyCode", required = false) String viewerCurrencyCode
    ) {
        MarketOfferDetailsResponse response = marketOfferService.getOffer(offerId, intent, viewerCurrencyCode);
        return ResponseEntity.ok(response);
    }
}
