package ru.maltsev.primemarketbackend.market.service;

import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.category.domain.Category;
import ru.maltsev.primemarketbackend.category.repository.CategoryRepository;
import ru.maltsev.primemarketbackend.currency.repository.CurrencyRepository;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.game.repository.GameRepository;
import ru.maltsev.primemarketbackend.market.api.dto.MarketOfferDetailsResponse;
import ru.maltsev.primemarketbackend.market.api.dto.MarketOfferListRequest;
import ru.maltsev.primemarketbackend.market.api.dto.MarketOfferListResponse;
import ru.maltsev.primemarketbackend.market.repository.MarketOfferQueryRepository;
import ru.maltsev.primemarketbackend.market.repository.MarketOfferQueryRepository.MarketOfferAttributeRecord;
import ru.maltsev.primemarketbackend.market.repository.MarketOfferQueryRepository.MarketOfferContextRecord;
import ru.maltsev.primemarketbackend.market.repository.MarketOfferQueryRepository.MarketOfferDeliveryMethodRecord;
import ru.maltsev.primemarketbackend.market.repository.MarketOfferQueryRepository.MarketOfferPageData;
import ru.maltsev.primemarketbackend.market.repository.MarketOfferQueryRepository.MarketOfferRecord;
import ru.maltsev.primemarketbackend.market.repository.MarketOfferSearchCriteria;

@Service
@RequiredArgsConstructor
public class MarketOfferService {
    private static final String SUPPORTED_CATEGORY_SLUG = "currency";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final GameRepository gameRepository;
    private final CategoryRepository categoryRepository;
    private final CurrencyRepository currencyRepository;
    private final MarketOfferQueryRepository marketOfferQueryRepository;

    @Transactional(readOnly = true)
    public MarketOfferListResponse listOffers(MarketOfferListRequest request) {
        String gameSlug = requireSlug(request.gameSlug(), "gameSlug");
        String categorySlug = requireSlug(request.categorySlug(), "categorySlug");
        MarketIntent intent = MarketIntent.from(request.intent());
        String viewerCurrencyCode = requireValidViewerCurrencyCode(request.viewerCurrencyCode());
        int page = normalizePage(request.page());
        int size = normalizeSize(request.size());
        MarketPriceSort sort = MarketPriceSort.resolve(request.sort(), intent);

        gameRepository.findBySlugIgnoreCaseAndActiveTrue(gameSlug)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "GAME_NOT_FOUND",
                "Game not found"
            ));

        Category category = categoryRepository.findActiveByGameSlugAndCategorySlug(gameSlug, categorySlug)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "CATEGORY_NOT_FOUND",
                "Category not found"
            ));

        validateSupportedCategory(category);

        MarketOfferSearchCriteria criteria = new MarketOfferSearchCriteria(
            category.getGame().getId(),
            category.getId(),
            intent.offerSide(),
            viewerCurrencyCode,
            intent,
            sort,
            normalizeOptionalSlug(request.platform()),
            normalizeOptionalSlug(request.league()),
            normalizeOptionalSlug(request.mode()),
            normalizeOptionalSlug(request.ruthless()),
            normalizeOptionalSlug(request.currencyType()),
            page,
            size
        );

        MarketOfferPageData pageData = marketOfferQueryRepository.findCurrencyOffers(criteria);
        List<MarketOfferListResponse.Item> items = pageData.items().stream()
            .map(item -> toResponseItem(item, intent))
            .toList();

        return new MarketOfferListResponse(items, page, size, pageData.total());
    }

    @Transactional(readOnly = true)
    public MarketOfferDetailsResponse getOffer(Long offerId, String rawIntent, String rawViewerCurrencyCode) {
        MarketIntent intent = MarketIntent.from(rawIntent);
        String viewerCurrencyCode = requireValidViewerCurrencyCode(rawViewerCurrencyCode);

        MarketOfferRecord offer = marketOfferQueryRepository.findCurrencyOfferById(offerId, intent, viewerCurrencyCode)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "MARKET_OFFER_NOT_FOUND",
                "Market offer not found"
            ));

        return new MarketOfferDetailsResponse(
            offer.id(),
            offer.offerVersion(),
            offer.side(),
            intent.action(),
            toGame(offer),
            toCategory(offer),
            toOwner(offer),
            offer.title(),
            offer.description(),
            offer.tradeTerms(),
            toPrice(offer),
            offer.quantity(),
            offer.minTradeQuantity(),
            offer.maxTradeQuantity(),
            offer.quantityStep(),
            mapContexts(offer.contexts()),
            mapAttributes(offer.attributes()),
            mapDeliveryMethods(offer.deliveryMethods()),
            offer.publishedAt()
        );
    }

    private MarketOfferListResponse.Item toResponseItem(MarketOfferRecord item, MarketIntent intent) {
        return new MarketOfferListResponse.Item(
            item.id(),
            item.offerVersion(),
            item.side(),
            intent.action(),
            toGame(item),
            toCategory(item),
            toOwner(item),
            item.title(),
            toPrice(item),
            item.quantity(),
            item.minTradeQuantity(),
            item.maxTradeQuantity(),
            item.quantityStep(),
            mapContexts(item.contexts()),
            mapAttributes(item.attributes()),
            mapDeliveryMethods(item.deliveryMethods()),
            item.publishedAt()
        );
    }

    private MarketOfferListResponse.Game toGame(MarketOfferRecord item) {
        return new MarketOfferListResponse.Game(item.gameId(), item.gameSlug(), item.gameTitle());
    }

    private MarketOfferListResponse.Category toCategory(MarketOfferRecord item) {
        return new MarketOfferListResponse.Category(item.categoryId(), item.categorySlug(), item.categoryTitle());
    }

    private MarketOfferListResponse.Owner toOwner(MarketOfferRecord item) {
        return new MarketOfferListResponse.Owner(item.ownerUsername());
    }

    private MarketOfferListResponse.Price toPrice(MarketOfferRecord item) {
        return new MarketOfferListResponse.Price(item.displayPriceAmount(), item.viewerCurrencyCode(), item.rate());
    }

    private List<MarketOfferListResponse.Context> mapContexts(List<MarketOfferContextRecord> contexts) {
        return contexts.stream()
            .map(context -> new MarketOfferListResponse.Context(
                context.dimensionSlug(),
                context.valueSlug(),
                context.valueTitle()
            ))
            .toList();
    }

    private List<MarketOfferListResponse.Attribute> mapAttributes(List<MarketOfferAttributeRecord> attributes) {
        return attributes.stream()
            .map(attribute -> new MarketOfferListResponse.Attribute(
                attribute.attributeSlug(),
                attribute.optionSlug(),
                attribute.optionTitle()
            ))
            .toList();
    }

    private List<MarketOfferListResponse.DeliveryMethod> mapDeliveryMethods(
        List<MarketOfferDeliveryMethodRecord> deliveryMethods
    ) {
        return deliveryMethods.stream()
            .map(method -> new MarketOfferListResponse.DeliveryMethod(method.slug(), method.title()))
            .toList();
    }

    private String requireSlug(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Query parameter '%s' is required".formatted(parameterName)
            );
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String requireViewerCurrencyCode(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Query parameter 'viewerCurrencyCode' is required"
            );
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String requireValidViewerCurrencyCode(String value) {
        String normalized = requireViewerCurrencyCode(value);
        if (!currencyRepository.existsByCodeIgnoreCaseAndActiveTrue(normalized)) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_VIEWER_CURRENCY_CODE",
                "Unknown currency code " + normalized
            );
        }
        return normalized;
    }

    private String normalizeOptionalSlug(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void validateSupportedCategory(Category category) {
        if (!SUPPORTED_CATEGORY_SLUG.equals(category.getSlug())) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "UNSUPPORTED_MARKET_CATEGORY",
                "Market listing supports only category 'currency'"
            );
        }
    }

    private int normalizePage(Integer page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page < 0) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Query parameter 'page' must be greater than or equal to 0"
            );
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size <= 0) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Query parameter 'size' must be greater than 0"
            );
        }
        return size;
    }
}
