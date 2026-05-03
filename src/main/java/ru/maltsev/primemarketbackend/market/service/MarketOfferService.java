package ru.maltsev.primemarketbackend.market.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.attribute.domain.CategoryAttribute;
import ru.maltsev.primemarketbackend.attribute.domain.CategoryAttributeOption;
import ru.maltsev.primemarketbackend.attribute.repository.CategoryAttributeOptionRepository;
import ru.maltsev.primemarketbackend.attribute.repository.CategoryAttributeRepository;
import ru.maltsev.primemarketbackend.category.domain.Category;
import ru.maltsev.primemarketbackend.category.repository.CategoryRepository;
import ru.maltsev.primemarketbackend.context.domain.CategoryContextDimension;
import ru.maltsev.primemarketbackend.context.repository.CategoryContextDimensionRepository;
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
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final GameRepository gameRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryContextDimensionRepository categoryContextDimensionRepository;
    private final CategoryAttributeRepository categoryAttributeRepository;
    private final CategoryAttributeOptionRepository categoryAttributeOptionRepository;
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

        Map<String, String> contextFilters = normalizeContextFilters(category.getId(), request.contextFilters());
        Map<String, String> attributeFilters = normalizeAttributeFilters(category.getId(), request.attributeFilters());

        MarketOfferSearchCriteria criteria = new MarketOfferSearchCriteria(
            category.getGame().getId(),
            category.getId(),
            intent.offerSide(),
            viewerCurrencyCode,
            intent,
            sort,
            contextFilters,
            attributeFilters,
            page,
            size
        );

        MarketOfferPageData pageData = marketOfferQueryRepository.findOffers(criteria);
        List<MarketOfferListResponse.Item> items = pageData.items().stream()
            .map(item -> toResponseItem(item, intent))
            .toList();

        return new MarketOfferListResponse(items, page, size, pageData.total());
    }

    @Transactional(readOnly = true)
    public MarketOfferDetailsResponse getOffer(Long offerId, String rawIntent, String rawViewerCurrencyCode) {
        MarketIntent intent = MarketIntent.from(rawIntent);
        String viewerCurrencyCode = requireValidViewerCurrencyCode(rawViewerCurrencyCode);

        MarketOfferRecord offer = marketOfferQueryRepository.findOfferById(offerId, intent, viewerCurrencyCode)
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
                attribute.attributeTitle(),
                attribute.optionSlug(),
                attribute.optionTitle(),
                attribute.valueText(),
                attribute.valueNumber(),
                attribute.valueBoolean()
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

    private Map<String, String> normalizeContextFilters(Long categoryId, Map<String, String> rawFilters) {
        if (rawFilters == null || rawFilters.isEmpty()) {
            return Map.of();
        }

        Map<String, CategoryContextDimension> allowedBySlug = new HashMap<>();
        for (CategoryContextDimension link : categoryContextDimensionRepository.findActiveByCategoryId(categoryId)) {
            String slug = normalizeOptionalSlug(link.getContextDimension().getSlug());
            if (slug != null) {
                allowedBySlug.put(slug, link);
            }
        }

        Map<String, String> normalized = new HashMap<>();
        for (Map.Entry<String, String> entry : rawFilters.entrySet()) {
            String slug = normalizeOptionalSlug(entry.getKey());
            String valueSlug = normalizeOptionalSlug(entry.getValue());
            if (slug == null || valueSlug == null) {
                continue;
            }
            if (!allowedBySlug.containsKey(slug)) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_MARKET_CONTEXT_FILTER",
                    "Context filter " + slug + " is not allowed for category"
                );
            }
            normalized.put(slug, valueSlug);
        }
        return normalized;
    }

    private Map<String, String> normalizeAttributeFilters(Long categoryId, Map<String, String> rawFilters) {
        if (rawFilters == null || rawFilters.isEmpty()) {
            return Map.of();
        }

        Map<String, CategoryAttribute> allowedBySlug = new HashMap<>();
        List<CategoryAttribute> attributes = categoryAttributeRepository.findActiveByCategoryId(categoryId);
        for (CategoryAttribute attribute : attributes) {
            String slug = normalizeOptionalSlug(attribute.getSlug());
            if (slug != null) {
                allowedBySlug.put(slug, attribute);
            }
        }

        List<Long> optionAttributeIds = attributes.stream()
            .filter(attribute -> {
                String dataType = normalizeDataType(attribute.getDataType());
                return "select".equals(dataType) || "multiselect".equals(dataType);
            })
            .map(CategoryAttribute::getId)
            .toList();
        Map<Long, Map<String, CategoryAttributeOption>> optionsByAttributeId =
            loadAttributeOptions(optionAttributeIds);

        Map<String, String> normalized = new HashMap<>();
        for (Map.Entry<String, String> entry : rawFilters.entrySet()) {
            String slug = normalizeOptionalSlug(entry.getKey());
            String optionSlug = normalizeOptionalSlug(entry.getValue());
            if (slug == null || optionSlug == null) {
                continue;
            }
            CategoryAttribute attribute = allowedBySlug.get(slug);
            if (attribute == null) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_MARKET_ATTRIBUTE_FILTER",
                    "Attribute filter " + slug + " is not allowed for category"
                );
            }
            String dataType = normalizeDataType(attribute.getDataType());
            if (!"select".equals(dataType) && !"multiselect".equals(dataType)) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_MARKET_ATTRIBUTE_FILTER",
                    "Attribute filter " + slug + " must target select attribute"
                );
            }
            Map<String, CategoryAttributeOption> options = optionsByAttributeId.get(attribute.getId());
            if (options == null || !options.containsKey(optionSlug)) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_MARKET_ATTRIBUTE_OPTION",
                    "Attribute option " + optionSlug + " is not allowed for category"
                );
            }
            normalized.put(slug, optionSlug);
        }
        return normalized;
    }

    private Map<Long, Map<String, CategoryAttributeOption>> loadAttributeOptions(List<Long> attributeIds) {
        Map<Long, Map<String, CategoryAttributeOption>> optionsByAttributeId = new HashMap<>();
        if (attributeIds.isEmpty()) {
            return optionsByAttributeId;
        }
        for (CategoryAttributeOption option : categoryAttributeOptionRepository.findActiveByAttributeIds(attributeIds)) {
            String slug = normalizeOptionalSlug(option.getSlug());
            if (slug == null) {
                continue;
            }
            optionsByAttributeId
                .computeIfAbsent(option.getCategoryAttribute().getId(), key -> new HashMap<>())
                .put(slug, option);
        }
        return optionsByAttributeId;
    }

    private String normalizeDataType(String dataType) {
        if (dataType == null) {
            return "";
        }
        return dataType.trim().toLowerCase(Locale.ROOT);
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
