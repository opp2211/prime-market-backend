package ru.maltsev.primemarketbackend.orderquote.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.currency.domain.CurrencyRate;
import ru.maltsev.primemarketbackend.currency.repository.CurrencyRateRepository;
import ru.maltsev.primemarketbackend.currency.repository.CurrencyRepository;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.market.api.dto.MarketOfferListResponse;
import ru.maltsev.primemarketbackend.market.service.MarketIntent;
import ru.maltsev.primemarketbackend.offer.service.OfferQuantityRules;
import ru.maltsev.primemarketbackend.offer.service.OfferQuantityRules.EffectiveLimits;
import ru.maltsev.primemarketbackend.offer.repository.OfferAttributeValueRepository;
import ru.maltsev.primemarketbackend.offer.repository.OfferContextValueRepository;
import ru.maltsev.primemarketbackend.offer.repository.OfferDeliveryMethodRepository;
import ru.maltsev.primemarketbackend.order.repository.OfferReservationRepository;
import ru.maltsev.primemarketbackend.order.service.FundsHoldService;
import ru.maltsev.primemarketbackend.orderquote.api.dto.CreateOrderQuoteRequest;
import ru.maltsev.primemarketbackend.orderquote.api.dto.OrderQuoteResponse;
import ru.maltsev.primemarketbackend.orderquote.domain.OrderQuote;
import ru.maltsev.primemarketbackend.orderquote.repository.OrderQuoteOfferProjection;
import ru.maltsev.primemarketbackend.orderquote.repository.OrderQuoteOfferRepository;
import ru.maltsev.primemarketbackend.orderquote.repository.OrderQuoteRepository;

@Service
@RequiredArgsConstructor
public class OrderQuoteService {
    private static final String SUPPORTED_CATEGORY_SLUG = "currency";
    private static final int DISPLAY_SCALE = 8;
    private static final long QUOTE_TTL_SECONDS = 60L;

    private final OrderQuoteRepository orderQuoteRepository;
    private final OrderQuoteOfferRepository orderQuoteOfferRepository;
    private final OfferContextValueRepository offerContextValueRepository;
    private final OfferAttributeValueRepository offerAttributeValueRepository;
    private final OfferDeliveryMethodRepository offerDeliveryMethodRepository;
    private final OfferReservationRepository offerReservationRepository;
    private final CurrencyRepository currencyRepository;
    private final CurrencyRateRepository currencyRateRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderQuoteResponse createQuote(Long offerId, CreateOrderQuoteRequest request) {
        MarketIntent intent = MarketIntent.fromBody(request.intent());
        String viewerCurrencyCode = requireValidViewerCurrencyCode(request.viewerCurrencyCode());
        QuotePayload payload = prepareQuotePayload(offerId, intent, viewerCurrencyCode);
        OrderQuote quote = orderQuoteRepository.save(buildQuote(payload));
        return toResponse(
            quote,
            payload,
            !numbersEqual(payload.displayUnitPriceAmount(), request.listedUnitPriceAmount()),
            !Objects.equals(payload.offer().offerVersion(), request.listedOfferVersion())
        );
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public OrderQuoteResponse refreshQuote(UUID quoteId) {
        OrderQuote existingQuote = orderQuoteRepository.findByPublicId(quoteId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "ORDER_QUOTE_NOT_FOUND",
                "Order quote not found"
            ));

        if (existingQuote.isConsumed()) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "ORDER_QUOTE_CONSUMED",
                "Order quote is already consumed"
            );
        }
        if (existingQuote.isInvalidated()) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "ORDER_QUOTE_INVALIDATED",
                "Order quote is already invalidated"
            );
        }

        Instant now = Instant.now();
        boolean wasActiveAndUnexpired = existingQuote.isActive() && existingQuote.getExpiresAt().isAfter(now);
        boolean expiredNow = existingQuote.isActive() && !existingQuote.getExpiresAt().isAfter(now);
        if (expiredNow) {
            existingQuote.markExpired();
            orderQuoteRepository.save(existingQuote);
        }

        MarketIntent intent = MarketIntent.fromBody(existingQuote.getIntent());
        QuotePayload payload = prepareQuotePayload(existingQuote.getOfferId(), intent, existingQuote.getViewerCurrencyCode());
        OrderQuote newQuote = orderQuoteRepository.save(buildQuote(payload));

        if (wasActiveAndUnexpired) {
            existingQuote.markInvalidated();
            orderQuoteRepository.save(existingQuote);
        }

        return toResponse(
            newQuote,
            payload,
            !numbersEqual(payload.displayUnitPriceAmount(), existingQuote.getDisplayUnitPriceAmount()),
            !Objects.equals(payload.offer().offerVersion(), existingQuote.getOfferVersionSnapshot())
        );
    }

    private QuotePayload prepareQuotePayload(Long offerId, MarketIntent intent, String viewerCurrencyCode) {
        OrderQuoteOfferProjection rawOffer = orderQuoteOfferRepository.findProjectionByOfferId(offerId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "OFFER_NOT_FOUND",
                "Offer not found"
            ));

        OrderQuoteOfferProjection offer = adjustAvailableQuantity(rawOffer);
        ensureOfferIsPubliclyAvailable(offer, intent);
        QuotePrice price = resolveQuotePrice(offer, intent, viewerCurrencyCode);
        List<MarketOfferListResponse.Context> contexts = loadContexts(offer.id());
        List<MarketOfferListResponse.Attribute> attributes = loadAttributes(offer.id());
        List<MarketOfferListResponse.DeliveryMethod> deliveryMethods = loadDeliveryMethods(offer.id());
        return new QuotePayload(
            offer,
            intent,
            viewerCurrencyCode,
            price.fxFromCurrencyCode(),
            price.fxToCurrencyCode(),
            price.fxRate(),
            price.displayUnitPriceAmount(),
            contexts,
            attributes,
            deliveryMethods
        );
    }

    private OrderQuoteOfferProjection adjustAvailableQuantity(OrderQuoteOfferProjection offer) {
        if (offer.quantity() == null) {
            return offer;
        }
        BigDecimal activeReservedQuantity = offerReservationRepository.sumQuantityByOfferIdAndStatus(
            offer.id(),
            FundsHoldService.STATUS_ACTIVE
        );
        BigDecimal availableQuantity = offer.quantity().subtract(activeReservedQuantity);
        if (availableQuantity.signum() < 0) {
            availableQuantity = BigDecimal.ZERO.setScale(offer.quantity().scale(), RoundingMode.HALF_UP);
        }
        EffectiveLimits effectiveLimits = OfferQuantityRules.calculateEffectiveLimits(
            availableQuantity,
            offer.minTradeQuantity(),
            offer.maxTradeQuantity(),
            offer.quantityStep()
        );
        return new OrderQuoteOfferProjection(
            offer.id(),
            offer.offerVersion(),
            offer.ownerUserId(),
            offer.ownerUsername(),
            offer.ownerActive(),
            offer.gameId(),
            offer.gameSlug(),
            offer.gameTitle(),
            offer.gameActive(),
            offer.categoryId(),
            offer.categorySlug(),
            offer.categoryTitle(),
            offer.categoryActive(),
            offer.side(),
            offer.status(),
            offer.title(),
            offer.description(),
            offer.tradeTerms(),
            offer.priceCurrencyCode(),
            offer.priceAmount(),
            effectiveLimits.availableQuantity(),
            effectiveLimits.effectiveMinTradeQuantity(),
            effectiveLimits.effectiveMaxTradeQuantity(),
            offer.quantityStep(),
            offer.publishedAt()
        );
    }

    private void ensureOfferIsPubliclyAvailable(OrderQuoteOfferProjection offer, MarketIntent intent) {
        if (!offer.ownerActive()
            || !offer.gameActive()
            || !offer.categoryActive()
            || !SUPPORTED_CATEGORY_SLUG.equals(offer.categorySlug())
            || !"active".equals(offer.status())
            || offer.publishedAt() == null
            || !intent.offerSide().equals(offer.side())
            || offer.priceAmount() == null
            || offer.priceCurrencyCode() == null
            || offer.quantity() == null
            || offer.quantity().signum() <= 0
            || offer.quantityStep() == null
            || offer.quantityStep().signum() <= 0
            || offer.maxTradeQuantity() == null
            || offer.maxTradeQuantity().signum() <= 0
            || (offer.minTradeQuantity() != null && offer.minTradeQuantity().compareTo(offer.maxTradeQuantity()) > 0)) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "OFFER_UNAVAILABLE",
                "Offer is unavailable for public quoting"
            );
        }
    }

    private QuotePrice resolveQuotePrice(OrderQuoteOfferProjection offer, MarketIntent intent, String viewerCurrencyCode) {
        String offerCurrencyCode = offer.priceCurrencyCode().toUpperCase(Locale.ROOT);
        if (offerCurrencyCode.equals(viewerCurrencyCode)) {
            return new QuotePrice(
                viewerCurrencyCode,
                viewerCurrencyCode,
                new BigDecimal("1.00000000"),
                offer.priceAmount().setScale(DISPLAY_SCALE, RoundingMode.HALF_UP)
            );
        }

        String fromCurrencyCode = intent == MarketIntent.BUY ? viewerCurrencyCode : offerCurrencyCode;
        String toCurrencyCode = intent == MarketIntent.BUY ? offerCurrencyCode : viewerCurrencyCode;
        CurrencyRate rate = currencyRateRepository
            .findByFromCurrencyCodeIgnoreCaseAndToCurrencyCodeIgnoreCase(fromCurrencyCode, toCurrencyCode)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.CONFLICT,
                "OFFER_UNAVAILABLE",
                "Offer is unavailable for public quoting"
            ));

        BigDecimal displayUnitPriceAmount = intent == MarketIntent.BUY
            ? offer.priceAmount().divide(rate.getRate(), DISPLAY_SCALE, RoundingMode.HALF_UP)
            : offer.priceAmount().multiply(rate.getRate()).setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);

        return new QuotePrice(
            fromCurrencyCode,
            toCurrencyCode,
            rate.getRate(),
            displayUnitPriceAmount
        );
    }

    private List<MarketOfferListResponse.Context> loadContexts(Long offerId) {
        return offerContextValueRepository.findAllByOfferId(offerId).stream()
            .map(value -> new MarketOfferListResponse.Context(
                value.getContextDimension().getSlug(),
                value.getContextDimensionValue().getSlug(),
                value.getContextDimensionValue().getTitle()
            ))
            .toList();
    }

    private List<MarketOfferListResponse.Attribute> loadAttributes(Long offerId) {
        return offerAttributeValueRepository.findAllByOfferId(offerId).stream()
            .map(value -> new MarketOfferListResponse.Attribute(
                value.getCategoryAttribute().getSlug(),
                value.getCategoryAttributeOption() == null ? null : value.getCategoryAttributeOption().getSlug(),
                value.getCategoryAttributeOption() == null ? value.getValueText() : value.getCategoryAttributeOption().getTitle()
            ))
            .toList();
    }

    private List<MarketOfferListResponse.DeliveryMethod> loadDeliveryMethods(Long offerId) {
        return offerDeliveryMethodRepository.findAllByOfferId(offerId).stream()
            .map(value -> new MarketOfferListResponse.DeliveryMethod(
                value.getDeliveryMethod().getSlug(),
                value.getDeliveryMethod().getTitle()
            ))
            .toList();
    }

    private String requireValidViewerCurrencyCode(String viewerCurrencyCode) {
        String normalized = viewerCurrencyCode.trim().toUpperCase(Locale.ROOT);
        if (!currencyRepository.existsByCodeIgnoreCaseAndActiveTrue(normalized)) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_VIEWER_CURRENCY_CODE",
                "Unknown currency code " + normalized
            );
        }
        return normalized;
    }

    private OrderQuote buildQuote(QuotePayload payload) {
        return new OrderQuote(
            UUID.randomUUID(),
            payload.offer().id(),
            payload.offer().offerVersion(),
            payload.intent().action(),
            payload.viewerCurrencyCode(),
            payload.offer().side(),
            payload.offer().ownerUserId(),
            payload.offer().ownerUsername(),
            payload.offer().gameId(),
            payload.offer().gameSlug(),
            payload.offer().gameTitle(),
            payload.offer().categoryId(),
            payload.offer().categorySlug(),
            payload.offer().categoryTitle(),
            payload.offer().title(),
            payload.offer().description(),
            payload.offer().tradeTerms(),
            payload.offer().publishedAt(),
            payload.offer().quantity(),
            payload.offer().minTradeQuantity(),
            payload.offer().maxTradeQuantity(),
            payload.offer().quantityStep(),
            payload.offer().priceCurrencyCode(),
            payload.offer().priceAmount(),
            payload.fxFromCurrencyCode(),
            payload.fxToCurrencyCode(),
            payload.fxRate(),
            payload.displayUnitPriceAmount(),
            toJson(payload.contexts()),
            toJson(payload.attributes()),
            toJson(payload.deliveryMethods()),
            Instant.now().plusSeconds(QUOTE_TTL_SECONDS)
        );
    }

    private JsonNode toJson(Object value) {
        return objectMapper.valueToTree(value);
    }

    private OrderQuoteResponse toResponse(
        OrderQuote quote,
        QuotePayload payload,
        boolean priceChanged,
        boolean offerUpdated
    ) {
        OrderQuoteOfferProjection offer = payload.offer();
        return new OrderQuoteResponse(
            quote.getPublicId(),
            quote.getExpiresAt(),
            priceChanged,
            offerUpdated,
            offer.id(),
            offer.offerVersion(),
            offer.side(),
            payload.intent().action(),
            new MarketOfferListResponse.Game(offer.gameId(), offer.gameSlug(), offer.gameTitle()),
            new MarketOfferListResponse.Category(offer.categoryId(), offer.categorySlug(), offer.categoryTitle()),
            new MarketOfferListResponse.Owner(offer.ownerUsername()),
            offer.title(),
            offer.description(),
            offer.tradeTerms(),
            new MarketOfferListResponse.Price(
                payload.displayUnitPriceAmount(),
                payload.viewerCurrencyCode(),
                payload.fxRate()
            ),
            offer.quantity(),
            offer.minTradeQuantity(),
            offer.maxTradeQuantity(),
            offer.quantityStep(),
            payload.contexts(),
            payload.attributes(),
            payload.deliveryMethods(),
            offer.publishedAt()
        );
    }

    private boolean numbersEqual(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.compareTo(right) == 0;
    }

    private record QuotePrice(
        String fxFromCurrencyCode,
        String fxToCurrencyCode,
        BigDecimal fxRate,
        BigDecimal displayUnitPriceAmount
    ) {
    }

    private record QuotePayload(
        OrderQuoteOfferProjection offer,
        MarketIntent intent,
        String viewerCurrencyCode,
        String fxFromCurrencyCode,
        String fxToCurrencyCode,
        BigDecimal fxRate,
        BigDecimal displayUnitPriceAmount,
        List<MarketOfferListResponse.Context> contexts,
        List<MarketOfferListResponse.Attribute> attributes,
        List<MarketOfferListResponse.DeliveryMethod> deliveryMethods
    ) {
    }
}
