package ru.maltsev.primemarketbackend.market.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
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
    private static final String CONTEXT_FILTER_PREFIX = "context.";
    private static final String ATTRIBUTE_FILTER_PREFIX = "attribute.";
    private static final Set<String> RESERVED_QUERY_PARAMS = Set.of(
        "gameSlug",
        "categorySlug",
        "intent",
        "viewerCurrencyCode",
        "page",
        "size",
        "sort"
    );
    private static final Map<String, String> LEGACY_CONTEXT_FILTER_ALIASES = Map.of(
        "platform", "platform",
        "league", "league",
        "mode", "mode",
        "ruthless", "ruthless",
        "server", "server"
    );
    private static final Map<String, String> LEGACY_ATTRIBUTE_FILTER_ALIASES = Map.of(
        "currencyType", "currency-type"
    );

    private final MarketOfferService marketOfferService;

    @GetMapping
    @Operation(
        summary = "List market offers",
        description = "`gameSlug`, `categorySlug`, `intent`, and `viewerCurrencyCode` are runtime-required even though the controller keeps them optional to preserve the current validation flow. Category filters can be sent as `context.<slug>=<valueSlug>` and `attribute.<slug>=<optionSlug>`.",
        parameters = {
            @Parameter(
                name = "context.<slug>",
                in = ParameterIn.QUERY,
                description = "Optional category context filter, for example `context.platform=pc` or `context.server=europe`."
            ),
            @Parameter(
                name = "attribute.<slug>",
                in = ParameterIn.QUERY,
                description = "Optional select/multiselect attribute filter, for example `attribute.currency-type=chaos-orb`."
            )
        }
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
        @Parameter(description = "Zero-based page number.", schema = @Schema(type = "integer", minimum = "0"))
        @RequestParam(name = "page", required = false) Integer page,
        @Parameter(description = "Page size. Defaults to 20 when omitted.", schema = @Schema(type = "integer", minimum = "1"))
        @RequestParam(name = "size", required = false) Integer size,
        @Parameter(
            description = "Explicit sort order. When omitted, backend uses `price_asc` for `buy` and `price_desc` for `sell`.",
            schema = @Schema(type = "string", allowableValues = { "price_asc", "price_desc" })
        )
        @RequestParam(name = "sort", required = false) String sort,
        @Parameter(hidden = true)
        @RequestParam MultiValueMap<String, String> allParams
    ) {
        MarketOfferListResponse response = marketOfferService.listOffers(new MarketOfferListRequest(
            gameSlug,
            categorySlug,
            intent,
            viewerCurrencyCode,
            extractContextFilters(allParams),
            extractAttributeFilters(allParams),
            page,
            size,
            sort
        ));
        return ResponseEntity.ok(response);
    }

    private Map<String, String> extractContextFilters(MultiValueMap<String, String> params) {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key == null || RESERVED_QUERY_PARAMS.contains(key)) {
                continue;
            }
            if (key.startsWith(CONTEXT_FILTER_PREFIX)) {
                putFilter(filters, key.substring(CONTEXT_FILTER_PREFIX.length()), firstValue(entry.getValue()));
                continue;
            }
            String legacySlug = LEGACY_CONTEXT_FILTER_ALIASES.get(key);
            if (legacySlug != null) {
                putFilter(filters, legacySlug, firstValue(entry.getValue()));
            }
        }
        return filters;
    }

    private Map<String, String> extractAttributeFilters(MultiValueMap<String, String> params) {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key == null || RESERVED_QUERY_PARAMS.contains(key)) {
                continue;
            }
            if (key.startsWith(ATTRIBUTE_FILTER_PREFIX)) {
                putFilter(filters, key.substring(ATTRIBUTE_FILTER_PREFIX.length()), firstValue(entry.getValue()));
                continue;
            }
            String legacySlug = LEGACY_ATTRIBUTE_FILTER_ALIASES.get(key);
            if (legacySlug != null) {
                putFilter(filters, legacySlug, firstValue(entry.getValue()));
            }
        }
        return filters;
    }

    private void putFilter(Map<String, String> filters, String rawSlug, String rawValue) {
        String slug = normalizeSlug(rawSlug);
        String value = normalizeSlug(rawValue);
        if (slug == null || value == null) {
            return;
        }
        filters.put(slug, value);
    }

    private String firstValue(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    private String normalizeSlug(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    @GetMapping("/{offerCode}")
    @Operation(
        summary = "Get market offer details",
        description = "`intent` and `viewerCurrencyCode` are runtime-required and affect both trade direction and display price."
    )
    public ResponseEntity<MarketOfferDetailsResponse> getOffer(
        @PathVariable String offerCode,
        @Parameter(
            required = true,
            description = "Requested market intent.",
            schema = @Schema(type = "string", allowableValues = { "buy", "sell" })
        )
        @RequestParam(name = "intent", required = false) String intent,
        @Parameter(required = true, description = "Viewer currency code used for display price.", example = "RUB")
        @RequestParam(name = "viewerCurrencyCode", required = false) String viewerCurrencyCode
    ) {
        MarketOfferDetailsResponse response = marketOfferService.getOffer(offerCode, intent, viewerCurrencyCode);
        return ResponseEntity.ok(response);
    }
}
