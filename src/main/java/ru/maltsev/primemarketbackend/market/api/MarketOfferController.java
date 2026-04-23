package ru.maltsev.primemarketbackend.market.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Operation(
        summary = "List market offers",
        description = "`gameSlug`, `categorySlug`, `intent`, and `viewerCurrencyCode` are runtime-required even though the controller keeps them optional to preserve the current validation flow."
    )
    public ResponseEntity<MarketOfferListResponse> listOffers(
        @Parameter(required = true, description = "Required game slug.", example = "lineage-2")
        @RequestParam(name = "gameSlug", required = false) String gameSlug,
        @Parameter(required = true, description = "Required category slug.", example = "currency")
        @RequestParam(name = "categorySlug", required = false) String categorySlug,
        @Parameter(
            required = true,
            description = "Requested market intent.",
            schema = @Schema(type = "string", allowableValues = { "buy", "sell" })
        )
        @RequestParam(name = "intent", required = false) String intent,
        @Parameter(required = true, description = "Viewer currency code used for display price.", example = "RUB")
        @RequestParam(name = "viewerCurrencyCode", required = false) String viewerCurrencyCode,
        @RequestParam(name = "platform", required = false) String platform,
        @RequestParam(name = "league", required = false) String league,
        @RequestParam(name = "mode", required = false) String mode,
        @RequestParam(name = "ruthless", required = false) String ruthless,
        @RequestParam(name = "currencyType", required = false) String currencyType,
        @Parameter(description = "Zero-based page number.", schema = @Schema(type = "integer", minimum = "0"))
        @RequestParam(name = "page", required = false) Integer page,
        @Parameter(description = "Page size. Defaults to 20 when omitted.", schema = @Schema(type = "integer", minimum = "1"))
        @RequestParam(name = "size", required = false) Integer size,
        @Parameter(
            description = "Explicit sort order. When omitted, backend uses `price_asc` for `buy` and `price_desc` for `sell`.",
            schema = @Schema(type = "string", allowableValues = { "price_asc", "price_desc" })
        )
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
    @Operation(
        summary = "Get market offer details",
        description = "`intent` and `viewerCurrencyCode` are runtime-required and affect both trade direction and display price."
    )
    public ResponseEntity<MarketOfferDetailsResponse> getOffer(
        @PathVariable Long offerId,
        @Parameter(
            required = true,
            description = "Requested market intent.",
            schema = @Schema(type = "string", allowableValues = { "buy", "sell" })
        )
        @RequestParam(name = "intent", required = false) String intent,
        @Parameter(required = true, description = "Viewer currency code used for display price.", example = "RUB")
        @RequestParam(name = "viewerCurrencyCode", required = false) String viewerCurrencyCode
    ) {
        MarketOfferDetailsResponse response = marketOfferService.getOffer(offerId, intent, viewerCurrencyCode);
        return ResponseEntity.ok(response);
    }
}
