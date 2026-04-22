package ru.maltsev.primemarketbackend.delivery.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.maltsev.primemarketbackend.delivery.repository.DeliveryMethodRepository;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferSchemaDeliveryMethodResponse;

@Service
@RequiredArgsConstructor
public class DeliveryMethodService {
    private final DeliveryMethodRepository deliveryMethodRepository;

    public List<OfferSchemaDeliveryMethodResponse> getActiveByCategory(String gameSlug, String categorySlug) {
        return deliveryMethodRepository.findActiveByGameAndCategorySlug(gameSlug, categorySlug)
            .stream()
            .map(OfferSchemaDeliveryMethodResponse::from)
            .toList();
    }
}
