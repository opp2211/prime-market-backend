package ru.maltsev.primemarketbackend.offer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.maltsev.primemarketbackend.attribute.api.dto.CategoryAttributeResponse;
import ru.maltsev.primemarketbackend.attribute.service.CategoryAttributeService;
import ru.maltsev.primemarketbackend.category.repository.CategoryRepository;
import ru.maltsev.primemarketbackend.context.api.dto.ContextResponse;
import ru.maltsev.primemarketbackend.context.service.ContextService;
import ru.maltsev.primemarketbackend.delivery.service.DeliveryMethodService;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferSchemaDeliveryMethodResponse;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferSchemaResponse;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferSchemaTradeFieldResponse;
import ru.maltsev.primemarketbackend.tradefield.service.CategoryTradeFieldConfigService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OfferSchemaService {
    private final CategoryRepository categoryRepository;
    private final ContextService contextService;
    private final CategoryAttributeService categoryAttributeService;
    private final CategoryTradeFieldConfigService categoryTradeFieldConfigService;
    private final DeliveryMethodService deliveryMethodService;

    public OfferSchemaResponse getOfferSchema(String gameSlug, String categorySlug) {
        categoryRepository.findActiveByGameSlugAndCategorySlug(gameSlug, categorySlug)
            .orElseThrow(() -> new ru.maltsev.primemarketbackend.exception.ApiProblemException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "CATEGORY_NOT_FOUND",
                "Category not found"
            ));
        List<ContextResponse> contexts = contextService.getCategoryContexts(gameSlug, categorySlug);
        List<CategoryAttributeResponse> attributes =
                categoryAttributeService.getActiveCategoryAttributes(gameSlug, categorySlug);
        List<OfferSchemaTradeFieldResponse> tradeFields =
                categoryTradeFieldConfigService.getByCategory(gameSlug, categorySlug);
        List<OfferSchemaDeliveryMethodResponse> deliveryMethods =
                deliveryMethodService.getActiveByCategory(gameSlug, categorySlug);
        return new OfferSchemaResponse(contexts, attributes, tradeFields, deliveryMethods);
    }
}
