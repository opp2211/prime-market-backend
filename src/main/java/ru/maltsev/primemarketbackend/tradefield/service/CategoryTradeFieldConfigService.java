package ru.maltsev.primemarketbackend.tradefield.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.maltsev.primemarketbackend.offer.api.dto.OfferSchemaTradeFieldResponse;
import ru.maltsev.primemarketbackend.offer.service.TradeFieldDictionary;
import ru.maltsev.primemarketbackend.tradefield.repository.CategoryTradeFieldConfigRepository;

@Service
@RequiredArgsConstructor
public class CategoryTradeFieldConfigService {
    private final CategoryTradeFieldConfigRepository categoryTradeFieldConfigRepository;

    public List<OfferSchemaTradeFieldResponse> getByCategory(String gameSlug, String categorySlug) {
        return categoryTradeFieldConfigRepository.findByGameAndCategorySlug(gameSlug, categorySlug)
            .stream()
            .map(config -> {
                TradeFieldDictionary.TradeFieldMeta meta =
                    TradeFieldDictionary.get(config.getFieldSlug(), config.isMultiselect());
                return OfferSchemaTradeFieldResponse.from(config, meta.title(), meta.dataType());
            })
            .toList();
    }
}
