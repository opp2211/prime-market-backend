package ru.maltsev.primemarketbackend.offer.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.attribute.domain.CategoryAttribute;
import ru.maltsev.primemarketbackend.attribute.domain.CategoryAttributeOption;
import ru.maltsev.primemarketbackend.attribute.repository.CategoryAttributeOptionRepository;
import ru.maltsev.primemarketbackend.attribute.repository.CategoryAttributeRepository;
import ru.maltsev.primemarketbackend.category.repository.CategoryRepository;
import ru.maltsev.primemarketbackend.delivery.domain.DeliveryMethod;
import ru.maltsev.primemarketbackend.delivery.repository.DeliveryMethodRepository;
import ru.maltsev.primemarketbackend.currency.repository.CurrencyRepository;
import ru.maltsev.primemarketbackend.context.domain.CategoryContextDimension;
import ru.maltsev.primemarketbackend.context.domain.ContextDimension;
import ru.maltsev.primemarketbackend.context.domain.ContextDimensionValue;
import ru.maltsev.primemarketbackend.context.repository.CategoryContextDimensionRepository;
import ru.maltsev.primemarketbackend.context.repository.ContextDimensionValueRepository;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferContextRequest;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferAttributeRequest;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferCreateRequest;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferUpdateRequest;
import ru.maltsev.primemarketbackend.offer.domain.Offer;
import ru.maltsev.primemarketbackend.offer.domain.OfferAttributeValue;
import ru.maltsev.primemarketbackend.offer.domain.OfferContextValue;
import ru.maltsev.primemarketbackend.offer.domain.OfferDeliveryMethod;
import ru.maltsev.primemarketbackend.offer.repository.OfferAttributeValueRepository;
import ru.maltsev.primemarketbackend.offer.repository.OfferContextValueRepository;
import ru.maltsev.primemarketbackend.offer.repository.OfferDeliveryMethodRepository;
import ru.maltsev.primemarketbackend.offer.repository.OfferRepository;
import ru.maltsev.primemarketbackend.offer.repository.OfferView;
import ru.maltsev.primemarketbackend.tradefield.domain.CategoryTradeFieldConfig;
import ru.maltsev.primemarketbackend.tradefield.repository.CategoryTradeFieldConfigRepository;

@Service
@RequiredArgsConstructor
public class OfferService {
    private static final Set<String> VALID_SIDES = Set.of("buy", "sell");
    private static final Set<String> VALID_STATUSES = Set.of("draft", "active", "paused", "closed", "deleted");
    private static final Set<String> CREATE_ALLOWED_STATUSES = Set.of("draft", "active");
    private static final Map<String, Set<String>> ALLOWED_STATUS_TRANSITIONS = Map.of(
        "draft", Set.of("draft", "active"),
        "active", Set.of("active", "paused", "closed"),
        "paused", Set.of("paused", "active", "closed"),
        "closed", Set.of("closed"),
        "deleted", Set.of("deleted")
    );
    private static final String FIELD_QUANTITY = "quantity";
    private static final String FIELD_MIN_TRADE_QUANTITY = "min-trade-quantity";
    private static final String FIELD_MAX_TRADE_QUANTITY = "max-trade-quantity";
    private static final String FIELD_QUANTITY_STEP = "quantity-step";
    private static final String FIELD_TRADE_TERMS = "trade-terms";
    private static final String FIELD_DELIVERY_METHODS = "delivery-methods";

    private final OfferRepository offerRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryContextDimensionRepository categoryContextDimensionRepository;
    private final ContextDimensionValueRepository contextDimensionValueRepository;
    private final OfferContextValueRepository offerContextValueRepository;
    private final CategoryAttributeRepository categoryAttributeRepository;
    private final CategoryAttributeOptionRepository categoryAttributeOptionRepository;
    private final OfferAttributeValueRepository offerAttributeValueRepository;
    private final DeliveryMethodRepository deliveryMethodRepository;
    private final OfferDeliveryMethodRepository offerDeliveryMethodRepository;
    private final CategoryTradeFieldConfigRepository categoryTradeFieldConfigRepository;
    private final CurrencyRepository currencyRepository;

    @Transactional
    public Offer create(Long userId, OfferCreateRequest request) {
        String side = normalizeSide(request.side());
        String status = normalizeCreateStatus(request.status());
        requireActiveCategory(request.gameId(), request.categoryId());

        Offer offer = new Offer(userId, request.gameId(), request.categoryId(), side, status);
        offer.setTitle(normalizeNullableText(request.title()));
        offer.setDescription(normalizeNullableText(request.description()));
        offer.setTradeTerms(normalizeNullableText(request.tradeTerms()));
        offer.setPriceCurrencyCode(normalizeCurrencyCode(request.priceCurrencyCode()));
        offer.setPriceAmount(request.priceAmount());
        offer.setQuantity(request.quantity());
        offer.setMinTradeQuantity(request.minTradeQuantity());
        offer.setMaxTradeQuantity(request.maxTradeQuantity());
        offer.setQuantityStep(request.quantityStep());

        validateNumericFields(offer);
        validatePriceCurrency(offer);

        boolean strict = "active".equals(status);
        if (strict) {
            validateTradeFieldsForActive(offer.getCategoryId(), offer, request.deliveryMethods(), null);
            validatePriceForActive(offer);
        }

        applyPublishedAtTransition(offer, null, status);

        Offer saved = offerRepository.save(offer);
        replaceContexts(saved, request.contexts(), strict);
        replaceAttributes(saved, request.attributes(), strict);
        replaceDeliveryMethods(saved, request.deliveryMethods());
        return saved;
    }

    public Offer getForUser(Long offerId, Long userId) {
        return offerRepository.findByIdAndUserId(offerId, userId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "OFFER_NOT_FOUND",
                "Offer not found"
            ));
    }

    public OfferView getViewForUser(Long offerId, Long userId) {
        return offerRepository.findViewByIdAndUserId(offerId, userId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "OFFER_NOT_FOUND",
                "Offer not found"
            ));
    }

    public List<OfferView> listViewsForUser(Long userId) {
        return offerRepository.findViewsByUserId(userId);
    }

    @Transactional
    public Offer update(Long offerId, Long userId, OfferUpdateRequest request) {
        Offer offer = getForUser(offerId, userId);
        validatePatchRequest(request);

        String previousStatus = offer.getStatus();
        validateEditableStatus(previousStatus, request);
        boolean categoryChanged = request.gameIdPresent() || request.categoryIdPresent();
        Long targetGameId = offer.getGameId();
        Long targetCategoryId = offer.getCategoryId();
        if (categoryChanged) {
            targetGameId = request.gameIdPresent() ? request.gameId() : offer.getGameId();
            targetCategoryId = request.categoryIdPresent() ? request.categoryId() : offer.getCategoryId();
            requireActiveCategory(targetGameId, targetCategoryId);
            offer.setGameId(targetGameId);
            offer.setCategoryId(targetCategoryId);
            if (!request.contextsPresent()) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "CONTEXTS_REQUIRED",
                    "Contexts are required when changing game or category"
                );
            }
            if (!request.attributesPresent()) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "ATTRIBUTES_REQUIRED",
                    "Attributes are required when changing game or category"
                );
            }
            if (!request.deliveryMethodsPresent()) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "DELIVERY_METHODS_REQUIRED",
                    "Delivery methods are required when changing game or category"
                );
            }
        }

        if (request.sidePresent()) {
            offer.setSide(normalizeSide(request.side()));
        }
        if (request.titlePresent()) {
            offer.setTitle(normalizeNullableText(request.title()));
        }
        if (request.descriptionPresent()) {
            offer.setDescription(normalizeNullableText(request.description()));
        }
        if (request.tradeTermsPresent()) {
            offer.setTradeTerms(normalizeNullableText(request.tradeTerms()));
        }
        if (request.priceCurrencyCodePresent()) {
            offer.setPriceCurrencyCode(normalizeCurrencyCode(request.priceCurrencyCode()));
        }
        if (request.priceAmountPresent()) {
            offer.setPriceAmount(request.priceAmount());
        }
        if (request.quantityPresent()) {
            offer.setQuantity(request.quantity());
        }
        if (request.minTradeQuantityPresent()) {
            offer.setMinTradeQuantity(request.minTradeQuantity());
        }
        if (request.maxTradeQuantityPresent()) {
            offer.setMaxTradeQuantity(request.maxTradeQuantity());
        }
        if (request.quantityStepPresent()) {
            offer.setQuantityStep(request.quantityStep());
        }
        if (request.statusPresent()) {
            String normalized = normalizeStatus(request.status());
            validateStatusTransition(previousStatus, normalized);
            offer.setStatus(normalized);
        }

        String targetStatus = offer.getStatus();
        boolean strict = "active".equals(targetStatus);

        validateNumericFields(offer);
        validatePriceCurrency(offer);

        if (strict) {
            validateTradeFieldsForActive(targetCategoryId, offer, request.deliveryMethodsPresent() ? request.deliveryMethods() : null, offerId);
            validatePriceForActive(offer);
            if (!request.contextsPresent()) {
                validateRequiredContexts(targetCategoryId, offerId);
            }
            if (!request.attributesPresent()) {
                validateRequiredAttributes(targetCategoryId, offerId);
            }
        }

        applyPublishedAtTransition(offer, previousStatus, targetStatus);

        Offer saved = offerRepository.save(offer);
        if (request.contextsPresent()) {
            replaceContexts(saved, request.contexts(), strict);
        }
        if (request.attributesPresent()) {
            replaceAttributes(saved, request.attributes(), strict);
        }
        if (request.deliveryMethodsPresent()) {
            replaceDeliveryMethods(saved, request.deliveryMethods());
        }
        return saved;
    }

    public List<OfferContextValue> getContextValues(Long offerId) {
        return offerContextValueRepository.findAllByOfferId(offerId);
    }

    public List<OfferAttributeValue> getAttributeValues(Long offerId) {
        return offerAttributeValueRepository.findAllByOfferId(offerId);
    }

    public List<String> getDeliveryMethods(Long offerId) {
        return offerDeliveryMethodRepository.findAllByOfferId(offerId).stream()
            .map(method -> method.getDeliveryMethod().getSlug())
            .toList();
    }

    private void requireActiveCategory(Long gameId, Long categoryId) {
        categoryRepository.findActiveByIdAndGameId(categoryId, gameId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "CATEGORY_NOT_FOUND",
                "Category not found"
            ));
    }

    private String normalizeSide(String side) {
        if (side == null || side.isBlank()) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "SIDE_REQUIRED", "Side is required");
        }
        String normalized = side.trim().toLowerCase(Locale.ROOT);
        if (!VALID_SIDES.contains(normalized)) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_SIDE",
                "Unknown side " + side
            );
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "STATUS_REQUIRED", "Status is required");
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(normalized)) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_STATUS",
                "Unknown status " + status
            );
        }
        return normalized;
    }

    private String normalizeCreateStatus(String status) {
        String normalized = normalizeStatus(status);
        if (!CREATE_ALLOWED_STATUSES.contains(normalized)) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_CREATE_STATUS",
                "Offer can only be created in draft or active status"
            );
        }
        return normalized;
    }

    private void validateStatusTransition(String currentStatus, String targetStatus) {
        Set<String> allowedTargets = ALLOWED_STATUS_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowedTargets.contains(targetStatus)) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_STATUS_TRANSITION",
                "Cannot change offer status from " + currentStatus + " to " + targetStatus
            );
        }
    }

    private void applyPublishedAtTransition(Offer offer, String previousStatus, String targetStatus) {
        if ("active".equals(targetStatus) && !"active".equals(previousStatus)) {
            offer.setPublishedAt(Instant.now());
        }
    }

    private void validatePatchRequest(OfferUpdateRequest request) {
        requirePatchValueIfPresent("gameId", request.gameIdPresent(), request.gameId(), "GAME_ID_NULL");
        requirePatchValueIfPresent("categoryId", request.categoryIdPresent(), request.categoryId(), "CATEGORY_ID_NULL");
        requirePatchValueIfPresent("side", request.sidePresent(), request.side(), "SIDE_NULL");
        requirePatchValueIfPresent("status", request.statusPresent(), request.status(), "STATUS_NULL");
        requirePatchArrayIfPresent("contexts", request.contextsPresent(), request.contexts());
        requirePatchArrayIfPresent("attributes", request.attributesPresent(), request.attributes());
        requirePatchArrayIfPresent("deliveryMethods", request.deliveryMethodsPresent(), request.deliveryMethods());
    }

    private void requirePatchValueIfPresent(String field, boolean present, Object value, String code) {
        if (present && value == null) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                code,
                field + " cannot be null"
            );
        }
    }

    private void requirePatchArrayIfPresent(String field, boolean present, List<?> value) {
        if (present && value == null) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "PATCH_ARRAY_NULL_NOT_ALLOWED",
                field + " cannot be null; omit the field to keep current value or send [] to clear it"
            );
        }
    }

    private void validateEditableStatus(String currentStatus, OfferUpdateRequest request) {
        if (!Set.of("closed", "deleted").contains(currentStatus)) {
            return;
        }
        boolean statusOnlyNoOp = request.statusPresent()
            && normalizeStatus(request.status()).equals(currentStatus)
            && !request.gameIdPresent()
            && !request.categoryIdPresent()
            && !request.sidePresent()
            && !request.titlePresent()
            && !request.descriptionPresent()
            && !request.tradeTermsPresent()
            && !request.priceCurrencyCodePresent()
            && !request.priceAmountPresent()
            && !request.quantityPresent()
            && !request.minTradeQuantityPresent()
            && !request.maxTradeQuantityPresent()
            && !request.quantityStepPresent()
            && !request.contextsPresent()
            && !request.attributesPresent()
            && !request.deliveryMethodsPresent();
        if (!statusOnlyNoOp) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "OFFER_NOT_EDITABLE",
                "Offers in " + currentStatus + " status cannot be edited"
            );
        }
    }

    private void replaceContexts(Offer offer, List<OfferContextRequest> rawContexts, boolean requireRequired) {
        List<OfferContextRequest> contexts = normalizeContexts(rawContexts);

        List<CategoryContextDimension> allowed = categoryContextDimensionRepository
            .findActiveByCategoryId(offer.getCategoryId());

        Map<String, CategoryContextDimension> allowedBySlug = new HashMap<>();
        Set<String> requiredSlugs = new HashSet<>();
        for (CategoryContextDimension link : allowed) {
            String slug = link.getContextDimension().getSlug();
            String normalizedSlug = slug == null ? null : slug.toLowerCase(Locale.ROOT);
            if (normalizedSlug != null) {
                allowedBySlug.put(normalizedSlug, link);
                if (link.isRequired()) {
                    requiredSlugs.add(normalizedSlug);
                }
            }
        }

        Set<String> providedSlugs = new HashSet<>();
        List<Long> dimensionIds = new ArrayList<>();
        for (OfferContextRequest context : contexts) {
            String dimensionSlug = context.dimensionSlug();
            CategoryContextDimension link = allowedBySlug.get(dimensionSlug);
            if (link == null) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CONTEXT_DIMENSION",
                    "Dimension " + dimensionSlug + " is not allowed for category"
                );
            }
            if (!providedSlugs.add(dimensionSlug)) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "DUPLICATE_CONTEXT_DIMENSION",
                    "Duplicate dimension " + dimensionSlug
                );
            }
            dimensionIds.add(link.getContextDimension().getId());
        }

        if (requireRequired && !providedSlugs.containsAll(requiredSlugs)) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "REQUIRED_CONTEXTS_MISSING",
                "Required contexts are missing"
            );
        }

        Map<Long, Map<String, ContextDimensionValue>> valuesByDimensionId = loadValues(dimensionIds);
        List<OfferContextValue> entities = new ArrayList<>();
        for (OfferContextRequest context : contexts) {
            ContextDimension dimension = allowedBySlug.get(context.dimensionSlug()).getContextDimension();
            ContextDimensionValue value = resolveValue(
                valuesByDimensionId,
                dimension.getId(),
                context.valueSlug()
            );
            entities.add(new OfferContextValue(offer, dimension, value));
        }

        offerContextValueRepository.deleteByOfferId(offer.getId());
        if (!entities.isEmpty()) {
            offerContextValueRepository.saveAll(entities);
        }
    }

    private void replaceAttributes(Offer offer, List<OfferAttributeRequest> rawAttributes, boolean requireRequired) {
        List<OfferAttributeRequest> attributes = normalizeAttributes(rawAttributes);

        List<CategoryAttribute> allowed = categoryAttributeRepository
            .findActiveByCategoryId(offer.getCategoryId());

        Map<String, CategoryAttribute> allowedBySlug = new HashMap<>();
        Set<String> requiredSlugs = new HashSet<>();
        for (CategoryAttribute attribute : allowed) {
            String slug = attribute.getSlug();
            String normalizedSlug = slug == null ? null : slug.toLowerCase(Locale.ROOT);
            if (normalizedSlug != null) {
                allowedBySlug.put(normalizedSlug, attribute);
                if (attribute.isRequired()) {
                    requiredSlugs.add(normalizedSlug);
                }
            }
        }

        Set<String> providedSlugs = new HashSet<>();
        List<Long> optionAttributeIds = new ArrayList<>();
        for (OfferAttributeRequest attributeRequest : attributes) {
            String attributeSlug = attributeRequest.attributeSlug();
            CategoryAttribute attribute = allowedBySlug.get(attributeSlug);
            if (attribute == null) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ATTRIBUTE",
                    "Attribute " + attributeSlug + " is not allowed for category"
                );
            }
            if (!providedSlugs.add(attributeSlug)) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "DUPLICATE_ATTRIBUTE",
                    "Duplicate attribute " + attributeSlug
                );
            }
            validateAttributeValue(attribute, attributeRequest);
            String dataType = normalizeDataType(attribute.getDataType());
            if ("select".equals(dataType) || "multiselect".equals(dataType)) {
                optionAttributeIds.add(attribute.getId());
            }
        }

        if (requireRequired && !providedSlugs.containsAll(requiredSlugs)) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "REQUIRED_ATTRIBUTES_MISSING",
                "Required attributes are missing"
            );
        }

        Map<Long, Map<String, CategoryAttributeOption>> optionsByAttributeId =
            loadAttributeOptions(optionAttributeIds);

        List<OfferAttributeValue> entities = new ArrayList<>();
        for (OfferAttributeRequest attributeRequest : attributes) {
            CategoryAttribute attribute = allowedBySlug.get(attributeRequest.attributeSlug());
            CategoryAttributeOption option = null;
            String dataType = normalizeDataType(attribute.getDataType());
            if ("select".equals(dataType) || "multiselect".equals(dataType)) {
                option = resolveAttributeOption(
                    optionsByAttributeId,
                    attribute.getId(),
                    attributeRequest.optionSlug()
                );
            }
            entities.add(new OfferAttributeValue(
                offer,
                attribute,
                option,
                attributeRequest.valueText(),
                attributeRequest.valueNumber(),
                attributeRequest.valueBoolean()
            ));
        }

        offerAttributeValueRepository.deleteByOfferId(offer.getId());
        if (!entities.isEmpty()) {
            offerAttributeValueRepository.saveAll(entities);
        }
    }

    private void replaceDeliveryMethods(Offer offer, List<String> rawMethods) {
        List<String> deliveryMethods = normalizeDeliveryMethods(rawMethods);

        if (deliveryMethods.isEmpty()) {
            offerDeliveryMethodRepository.deleteByOfferId(offer.getId());
            return;
        }

        List<DeliveryMethod> allowed = deliveryMethodRepository
            .findActiveByCategoryIdAndSlugIn(offer.getCategoryId(), deliveryMethods);
        Map<String, DeliveryMethod> allowedBySlug = new HashMap<>();
        for (DeliveryMethod method : allowed) {
            String slug = method.getSlug();
            if (slug != null) {
                allowedBySlug.put(slug, method);
            }
        }

        List<OfferDeliveryMethod> entities = new ArrayList<>();
        for (String slug : deliveryMethods) {
            DeliveryMethod method = allowedBySlug.get(slug);
            if (method == null) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DELIVERY_METHOD",
                    "Delivery method " + slug + " is not allowed for category"
                );
            }
            entities.add(new OfferDeliveryMethod(offer, method));
        }

        offerDeliveryMethodRepository.deleteByOfferId(offer.getId());
        offerDeliveryMethodRepository.saveAll(entities);
    }

    private Map<Long, Map<String, ContextDimensionValue>> loadValues(List<Long> dimensionIds) {
        Map<Long, Map<String, ContextDimensionValue>> valuesByDimensionId = new HashMap<>();
        if (dimensionIds.isEmpty()) {
            return valuesByDimensionId;
        }
        List<ContextDimensionValue> values = contextDimensionValueRepository.findActiveByDimensionIds(dimensionIds);
        for (ContextDimensionValue value : values) {
            valuesByDimensionId
                .computeIfAbsent(value.getContextDimension().getId(), key -> new HashMap<>())
                .put(value.getSlug(), value);
        }
        return valuesByDimensionId;
    }

    private ContextDimensionValue resolveValue(
        Map<Long, Map<String, ContextDimensionValue>> valuesByDimensionId,
        Long dimensionId,
        String valueSlug
    ) {
        Map<String, ContextDimensionValue> values = valuesByDimensionId.get(dimensionId);
        if (values == null || !values.containsKey(valueSlug)) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_CONTEXT_VALUE",
                "Value " + valueSlug + " does not belong to dimension"
            );
        }
        return values.get(valueSlug);
    }

    private List<OfferContextRequest> normalizeContexts(List<OfferContextRequest> rawContexts) {
        if (rawContexts == null) {
            return List.of();
        }

        List<OfferContextRequest> normalized = new ArrayList<>(rawContexts.size());
        for (OfferContextRequest context : rawContexts) {
            if (context == null) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "CONTEXT_REQUIRED",
                    "Context entry is required"
                );
            }
            String dimensionSlug = normalizeContextSlug(context.dimensionSlug(), "dimension");
            String valueSlug = normalizeContextSlug(context.valueSlug(), "value");
            normalized.add(new OfferContextRequest(dimensionSlug, valueSlug));
        }
        return normalized;
    }

    private String normalizeContextSlug(String slug, String field) {
        if (slug == null || slug.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "CONTEXT_" + field.toUpperCase(Locale.ROOT) + "_REQUIRED",
                "Context " + field + " slug is required"
            );
        }
        return slug.trim().toLowerCase(Locale.ROOT);
    }

    private List<OfferAttributeRequest> normalizeAttributes(List<OfferAttributeRequest> rawAttributes) {
        if (rawAttributes == null) {
            return List.of();
        }

        List<OfferAttributeRequest> normalized = new ArrayList<>(rawAttributes.size());
        for (OfferAttributeRequest attribute : rawAttributes) {
            if (attribute == null) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "ATTRIBUTE_REQUIRED",
                    "Attribute entry is required"
                );
            }
            String attributeSlug = normalizeAttributeSlug(attribute.attributeSlug());
            String optionSlug = attribute.optionSlug();
            if (optionSlug != null && !optionSlug.isBlank()) {
                optionSlug = optionSlug.trim().toLowerCase(Locale.ROOT);
            } else {
                optionSlug = null;
            }
            String valueText = attribute.valueText();
            if (valueText != null) {
                valueText = valueText.trim();
                if (valueText.isEmpty()) {
                    valueText = null;
                }
            }
            normalized.add(new OfferAttributeRequest(
                attributeSlug,
                optionSlug,
                valueText,
                attribute.valueNumber(),
                attribute.valueBoolean()
            ));
        }
        return normalized;
    }

    private List<String> normalizeDeliveryMethods(List<String> rawMethods) {
        if (rawMethods == null) {
            return List.of();
        }

        Set<String> unique = new HashSet<>();
        List<String> normalized = new ArrayList<>(rawMethods.size());
        for (String method : rawMethods) {
            if (method == null || method.isBlank()) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "DELIVERY_METHOD_REQUIRED",
                    "Delivery method slug is required"
                );
            }
            String slug = method.trim().toLowerCase(Locale.ROOT);
            if (!unique.add(slug)) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "DUPLICATE_DELIVERY_METHOD",
                    "Duplicate delivery method " + slug
                );
            }
            normalized.add(slug);
        }
        return normalized;
    }

    private void validateNumericFields(Offer offer) {
        requirePositive("priceAmount", offer.getPriceAmount(), "PRICE_AMOUNT_INVALID");
        requirePositive("quantity", offer.getQuantity(), "QUANTITY_INVALID");
        requirePositive("minTradeQuantity", offer.getMinTradeQuantity(), "MIN_TRADE_QUANTITY_INVALID");
        requirePositive("maxTradeQuantity", offer.getMaxTradeQuantity(), "MAX_TRADE_QUANTITY_INVALID");
        requirePositive("quantityStep", offer.getQuantityStep(), "QUANTITY_STEP_INVALID");

        if (offer.getMinTradeQuantity() != null && offer.getMaxTradeQuantity() != null
            && offer.getMinTradeQuantity().compareTo(offer.getMaxTradeQuantity()) > 0) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "MIN_TRADE_GT_MAX_TRADE",
                "minTradeQuantity must be less than or equal to maxTradeQuantity"
            );
        }
        if (offer.getMaxTradeQuantity() != null && offer.getQuantity() != null
            && offer.getMaxTradeQuantity().compareTo(offer.getQuantity()) > 0) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "MAX_TRADE_GT_QUANTITY",
                "maxTradeQuantity must be less than or equal to quantity"
            );
        }
        if (offer.getQuantityStep() != null) {
            requireAlignedWithStep("quantity", offer.getQuantity(), offer.getQuantityStep(), "QUANTITY_STEP_MISMATCH");
            requireAlignedWithStep(
                "minTradeQuantity",
                offer.getMinTradeQuantity(),
                offer.getQuantityStep(),
                "MIN_TRADE_QUANTITY_STEP_MISMATCH"
            );
            requireAlignedWithStep(
                "maxTradeQuantity",
                offer.getMaxTradeQuantity(),
                offer.getQuantityStep(),
                "MAX_TRADE_QUANTITY_STEP_MISMATCH"
            );
        }
    }

    private void validatePriceCurrency(Offer offer) {
        String currencyCode = offer.getPriceCurrencyCode();
        if (offer.getPriceAmount() != null && (currencyCode == null || currencyCode.isBlank())) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "PRICE_CURRENCY_REQUIRED",
                "priceCurrencyCode is required when priceAmount is set"
            );
        }
        if (offer.getPriceAmount() == null && currencyCode != null && !currencyCode.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "PRICE_AMOUNT_REQUIRED",
                "priceAmount is required when priceCurrencyCode is set"
            );
        }
        if (currencyCode != null) {
            String normalized = currencyCode.trim().toUpperCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                offer.setPriceCurrencyCode(null);
                return;
            }
            if (!currencyRepository.existsByCodeIgnoreCaseAndActiveTrue(normalized)) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "CURRENCY_NOT_FOUND",
                    "Unknown currency code " + currencyCode
                );
            }
            offer.setPriceCurrencyCode(normalized);
        }
    }

    private void validatePriceForActive(Offer offer) {
        if (offer.getPriceAmount() == null) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "PRICE_AMOUNT_REQUIRED",
                "priceAmount is required for active offer"
            );
        }
        if (offer.getPriceCurrencyCode() == null || offer.getPriceCurrencyCode().isBlank()) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "PRICE_CURRENCY_REQUIRED",
                "priceCurrencyCode is required for active offer"
            );
        }
    }

    private void validateTradeFieldsForActive(
        Long categoryId,
        Offer offer,
        List<String> requestDeliveryMethods,
        Long offerId
    ) {
        Map<String, CategoryTradeFieldConfig> configs = loadTradeFieldConfigs(categoryId);

        if (isRequired(configs.get(FIELD_QUANTITY)) && offer.getQuantity() == null) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "QUANTITY_REQUIRED",
                "Quantity is required for active offer"
            );
        }
        if (isRequired(configs.get(FIELD_MIN_TRADE_QUANTITY)) && offer.getMinTradeQuantity() == null) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "MIN_TRADE_QUANTITY_REQUIRED",
                "minTradeQuantity is required for active offer"
            );
        }
        if (isRequired(configs.get(FIELD_MAX_TRADE_QUANTITY)) && offer.getMaxTradeQuantity() == null) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "MAX_TRADE_QUANTITY_REQUIRED",
                "maxTradeQuantity is required for active offer"
            );
        }
        if (isRequired(configs.get(FIELD_QUANTITY_STEP)) && offer.getQuantityStep() == null) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "QUANTITY_STEP_REQUIRED",
                "quantityStep is required for active offer"
            );
        }
        if (isRequired(configs.get(FIELD_TRADE_TERMS))) {
            String tradeTerms = offer.getTradeTerms();
            if (tradeTerms == null || tradeTerms.isBlank()) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "TRADE_TERMS_REQUIRED",
                    "tradeTerms is required for active offer"
                );
            }
        }
        if (isRequired(configs.get(FIELD_DELIVERY_METHODS))) {
            List<String> methods = requestDeliveryMethods != null
                ? normalizeDeliveryMethods(requestDeliveryMethods)
                : null;
            if (methods != null) {
                if (methods.isEmpty()) {
                    throw new ApiProblemException(
                        HttpStatus.BAD_REQUEST,
                        "DELIVERY_METHODS_REQUIRED",
                        "At least one delivery method is required for active offer"
                    );
                }
            } else if (offerId != null) {
                if (offerDeliveryMethodRepository.findAllByOfferId(offerId).isEmpty()) {
                    throw new ApiProblemException(
                        HttpStatus.BAD_REQUEST,
                        "DELIVERY_METHODS_REQUIRED",
                        "At least one delivery method is required for active offer"
                    );
                }
            } else {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "DELIVERY_METHODS_REQUIRED",
                    "At least one delivery method is required for active offer"
                );
            }
        }
    }

    private Map<String, CategoryTradeFieldConfig> loadTradeFieldConfigs(Long categoryId) {
        Map<String, CategoryTradeFieldConfig> configs = new HashMap<>();
        List<CategoryTradeFieldConfig> rows = categoryTradeFieldConfigRepository.findByCategoryId(categoryId);
        for (CategoryTradeFieldConfig config : rows) {
            String slug = normalizeTradeFieldSlug(config.getFieldSlug());
            if (slug != null) {
                configs.put(slug, config);
            }
        }
        return configs;
    }

    private boolean isRequired(CategoryTradeFieldConfig config) {
        return config != null && config.isVisible() && config.isRequired();
    }

    private String normalizeTradeFieldSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        return slug.trim().toLowerCase(Locale.ROOT);
    }

    private void validateRequiredContexts(Long categoryId, Long offerId) {
        List<CategoryContextDimension> allowed = categoryContextDimensionRepository
            .findActiveByCategoryId(categoryId);
        Set<String> requiredSlugs = new HashSet<>();
        for (CategoryContextDimension link : allowed) {
            if (link.isRequired() && link.getContextDimension().getSlug() != null) {
                requiredSlugs.add(link.getContextDimension().getSlug().toLowerCase(Locale.ROOT));
            }
        }
        if (requiredSlugs.isEmpty()) {
            return;
        }
        Set<String> present = new HashSet<>();
        for (OfferContextValue value : offerContextValueRepository.findAllByOfferId(offerId)) {
            String slug = value.getContextDimension().getSlug();
            if (slug != null) {
                present.add(slug.toLowerCase(Locale.ROOT));
            }
        }
        if (!present.containsAll(requiredSlugs)) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "REQUIRED_CONTEXTS_MISSING",
                "Required contexts are missing"
            );
        }
    }

    private void validateRequiredAttributes(Long categoryId, Long offerId) {
        List<CategoryAttribute> allowed = categoryAttributeRepository
            .findActiveByCategoryId(categoryId);
        Set<String> requiredSlugs = new HashSet<>();
        for (CategoryAttribute attribute : allowed) {
            if (attribute.isRequired() && attribute.getSlug() != null) {
                requiredSlugs.add(attribute.getSlug().toLowerCase(Locale.ROOT));
            }
        }
        if (requiredSlugs.isEmpty()) {
            return;
        }
        Set<String> present = new HashSet<>();
        for (OfferAttributeValue value : offerAttributeValueRepository.findAllByOfferId(offerId)) {
            String slug = value.getCategoryAttribute().getSlug();
            if (slug != null) {
                present.add(slug.toLowerCase(Locale.ROOT));
            }
        }
        if (!present.containsAll(requiredSlugs)) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "REQUIRED_ATTRIBUTES_MISSING",
                "Required attributes are missing"
            );
        }
    }

    private void requirePositive(String field, BigDecimal value, String code) {
        if (value != null && value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                code,
                field + " must be greater than 0"
            );
        }
    }

    private void requireAlignedWithStep(String field, BigDecimal value, BigDecimal step, String code) {
        if (value == null || step == null) {
            return;
        }
        if (value.remainder(step).compareTo(BigDecimal.ZERO) != 0) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                code,
                field + " must be aligned with quantityStep"
            );
        }
    }

    private String normalizeCurrencyCode(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateAttributeValue(CategoryAttribute attribute, OfferAttributeRequest request) {
        String dataType = normalizeDataType(attribute.getDataType());
        boolean hasOption = request.optionSlug() != null && !request.optionSlug().isBlank();
        boolean hasText = request.valueText() != null && !request.valueText().isBlank();
        boolean hasNumber = request.valueNumber() != null;
        boolean hasBoolean = request.valueBoolean() != null;

        if ("select".equals(dataType) || "multiselect".equals(dataType)) {
            if (!hasOption || hasText || hasNumber || hasBoolean) {
                throw invalidAttributeValue(attribute.getSlug());
            }
            return;
        }
        if ("text".equals(dataType)) {
            if (!hasText || hasOption || hasNumber || hasBoolean) {
                throw invalidAttributeValue(attribute.getSlug());
            }
            return;
        }
        if ("number".equals(dataType)) {
            if (!hasNumber || hasOption || hasText || hasBoolean) {
                throw invalidAttributeValue(attribute.getSlug());
            }
            return;
        }
        if ("boolean".equals(dataType)) {
            if (!hasBoolean || hasOption || hasText || hasNumber) {
                throw invalidAttributeValue(attribute.getSlug());
            }
            return;
        }
        throw new ApiProblemException(
            HttpStatus.BAD_REQUEST,
            "INVALID_ATTRIBUTE_DATA_TYPE",
            "Unknown attribute data type " + attribute.getDataType()
        );
    }

    private ApiProblemException invalidAttributeValue(String attributeSlug) {
        return new ApiProblemException(
            HttpStatus.BAD_REQUEST,
            "INVALID_ATTRIBUTE_VALUE",
            "Value does not match attribute " + attributeSlug
        );
    }

    private String normalizeDataType(String dataType) {
        if (dataType == null) {
            return "";
        }
        return dataType.trim().toLowerCase(Locale.ROOT);
    }

    private Map<Long, Map<String, CategoryAttributeOption>> loadAttributeOptions(List<Long> attributeIds) {
        Map<Long, Map<String, CategoryAttributeOption>> optionsByAttributeId = new HashMap<>();
        if (attributeIds.isEmpty()) {
            return optionsByAttributeId;
        }
        List<CategoryAttributeOption> options = categoryAttributeOptionRepository
            .findActiveByAttributeIds(attributeIds);
        for (CategoryAttributeOption option : options) {
            optionsByAttributeId
                .computeIfAbsent(option.getCategoryAttribute().getId(), key -> new HashMap<>())
                .put(option.getSlug(), option);
        }
        return optionsByAttributeId;
    }

    private CategoryAttributeOption resolveAttributeOption(
        Map<Long, Map<String, CategoryAttributeOption>> optionsByAttributeId,
        Long attributeId,
        String optionSlug
    ) {
        Map<String, CategoryAttributeOption> options = optionsByAttributeId.get(attributeId);
        if (options == null || optionSlug == null || !options.containsKey(optionSlug)) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_ATTRIBUTE_OPTION",
                "Option " + optionSlug + " does not belong to attribute"
            );
        }
        return options.get(optionSlug);
    }

    private String normalizeAttributeSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "ATTRIBUTE_SLUG_REQUIRED",
                "Attribute slug is required"
            );
        }
        return slug.trim().toLowerCase(Locale.ROOT);
    }
}
