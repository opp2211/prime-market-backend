package ru.maltsev.primemarketbackend.market.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;
import ru.maltsev.primemarketbackend.account.service.UserAccountService;
import ru.maltsev.primemarketbackend.category.domain.Category;
import ru.maltsev.primemarketbackend.category.repository.CategoryRepository;
import ru.maltsev.primemarketbackend.offer.domain.Offer;
import ru.maltsev.primemarketbackend.offer.repository.OfferRepository;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.support.AbstractPostgresIntegrationTest;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class MarketOfferApiIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OfferRepository offerRepository;

    @Autowired
    private UserAccountService userAccountService;

    private final AtomicLong testRefIdSequence = new AtomicLong(1L);

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("""
            truncate table
                platform_account_txs,
                platform_accounts,
                user_account_hold_allocations,
                user_account_holds,
                offer_reservations,
                order_requests,
                orders,
                order_quotes,
                user_account_txs,
                user_accounts,
                offer_delivery_methods,
                offer_attribute_values,
                offer_context_values,
                offers
            restart identity cascade
            """);
        jdbcTemplate.update("delete from users where email like ?", "%.market.example.test");
    }

    @Test
    void marketListingUsesViewerToOfferRateForBuyIntent() throws Exception {
        User seller = createUser("seller-buy-intent");
        createActiveOffer(seller, "sell", "USD", "2.50", "divine-orb", "Sell Divine Orb");

        MvcResult result = mockMvc.perform(get("/api/market/offers")
                .queryParam("gameSlug", "path-of-exile")
                .queryParam("categorySlug", "currency")
                .queryParam("intent", "buy")
                .queryParam("viewerCurrencyCode", "RUB"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].offerVersion").value(1))
            .andExpect(jsonPath("$.items[0].side").value("sell"))
            .andExpect(jsonPath("$.items[0].action").value("buy"))
            .andExpect(jsonPath("$.items[0].price.currencyCode").value("RUB"))
            .andReturn();

        JsonNode item = readBody(result).path("items").get(0);
        assertThat(item.path("price").path("rate").decimalValue()).isEqualByComparingTo("0.01050000");
        assertThat(item.path("price").path("amount").decimalValue()).isEqualByComparingTo("238.09523810");
    }

    @Test
    void marketListingUsesOfferToViewerRateForSellIntent() throws Exception {
        User buyer = createUser("buyer-sell-intent");
        createActiveOffer(buyer, "buy", "USD", "2.50", "divine-orb", "Buy Divine Orb");

        MvcResult result = mockMvc.perform(get("/api/market/offers")
                .queryParam("gameSlug", "path-of-exile")
                .queryParam("categorySlug", "currency")
                .queryParam("intent", "sell")
                .queryParam("viewerCurrencyCode", "RUB"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].side").value("buy"))
            .andExpect(jsonPath("$.items[0].action").value("sell"))
            .andExpect(jsonPath("$.items[0].price.currencyCode").value("RUB"))
            .andReturn();

        JsonNode item = readBody(result).path("items").get(0);
        assertThat(item.path("price").path("rate").decimalValue()).isEqualByComparingTo("92.50000000");
        assertThat(item.path("price").path("amount").decimalValue()).isEqualByComparingTo("231.25000000");
    }

    @Test
    void marketListingFiltersByCurrencyType() throws Exception {
        User seller = createUser("seller-filter");
        createActiveOffer(seller, "sell", "USD", "2.50", "divine-orb", "Divine offer");
        createActiveOffer(seller, "sell", "USD", "3.50", "chaos-orb", "Chaos offer");

        mockMvc.perform(get("/api/market/offers")
                .queryParam("gameSlug", "path-of-exile")
                .queryParam("categorySlug", "currency")
                .queryParam("intent", "buy")
                .queryParam("viewerCurrencyCode", "RUB")
                .queryParam("currencyType", "chaos-orb"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].title").value("Chaos offer"))
            .andExpect(jsonPath("$.items[0].attributes[0].attributeSlug").value("currency-type"))
            .andExpect(jsonPath("$.items[0].attributes[0].optionSlug").value("chaos-orb"));
    }

    @Test
    void marketListingExcludesOffersWhenDirectRateIsMissing() throws Exception {
        User seller = createUser("seller-missing-rate");
        createActiveOffer(seller, "sell", "KZT", "1000.00", "divine-orb", "No RUB rate");

        mockMvc.perform(get("/api/market/offers")
                .queryParam("gameSlug", "path-of-exile")
                .queryParam("categorySlug", "currency")
                .queryParam("intent", "buy")
                .queryParam("viewerCurrencyCode", "RUB"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void marketListingSortsByDisplayPrice() throws Exception {
        User seller = createUser("seller-sort");
        createActiveOffer(seller, "sell", "USD", "2.00", "divine-orb", "USD offer");
        createActiveOffer(seller, "sell", "RUB", "150.00", "chaos-orb", "RUB offer");

        MvcResult result = mockMvc.perform(get("/api/market/offers")
                .queryParam("gameSlug", "path-of-exile")
                .queryParam("categorySlug", "currency")
                .queryParam("intent", "buy")
                .queryParam("viewerCurrencyCode", "RUB")
                .queryParam("sort", "price_asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andReturn();

        JsonNode items = readBody(result).path("items");
        assertThat(items.get(0).path("title").asText()).isEqualTo("RUB offer");
        assertThat(items.get(1).path("title").asText()).isEqualTo("USD offer");
        assertThat(items.get(0).path("price").path("amount").decimalValue()).isEqualByComparingTo("150.00000000");
        assertThat(items.get(1).path("price").path("amount").decimalValue()).isEqualByComparingTo("190.47619048");
    }

    @Test
    void marketListingReturnsEffectiveQuantityLimitsForRelaxedRawOfferRules() throws Exception {
        User seller = createUser("seller-effective-limits");
        Category category = requireCategory("path-of-exile", "currency");
        mockMvc.perform(post("/api/offers")
                .with(auth(seller))
                .contentType(MediaType.APPLICATION_JSON)
                .content(activeOfferRequest(
                    category.getGame().getId(),
                    category.getId(),
                    "sell",
                    "USD",
                    "2.50",
                    "divine-orb",
                    "Effective limits"
                ).replace("\"quantity\": 100", "\"quantity\": 101")
                    .replace("\"minTradeQuantity\": 10", "\"minTradeQuantity\": 12")
                    .replace("\"maxTradeQuantity\": 50", "\"maxTradeQuantity\": 250")
                    .replace("\"quantityStep\": 1", "\"quantityStep\": 5")))
            .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/market/offers")
                .queryParam("gameSlug", "path-of-exile")
                .queryParam("categorySlug", "currency")
                .queryParam("intent", "buy")
                .queryParam("viewerCurrencyCode", "RUB"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andReturn();

        JsonNode item = readBody(result).path("items").get(0);
        assertThat(item.path("quantity").decimalValue()).isEqualByComparingTo("101");
        assertThat(item.path("minTradeQuantity").decimalValue()).isEqualByComparingTo("15");
        assertThat(item.path("maxTradeQuantity").decimalValue()).isEqualByComparingTo("100");
        assertThat(item.path("quantityStep").decimalValue()).isEqualByComparingTo("5");
    }

    @Test
    void marketOfferDetailsUsesViewerToOfferRateForBuyIntent() throws Exception {
        User seller = createUser("seller-details-buy");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "divine-orb", "Sell Divine Orb");

        MvcResult result = mockMvc.perform(get("/api/market/offers/{offerId}", offerId)
                .queryParam("intent", "buy")
                .queryParam("viewerCurrencyCode", "RUB"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(offerId))
            .andExpect(jsonPath("$.side").value("sell"))
            .andExpect(jsonPath("$.action").value("buy"))
            .andExpect(jsonPath("$.price.currencyCode").value("RUB"))
            .andExpect(jsonPath("$.description").value("Fast trade"))
            .andExpect(jsonPath("$.tradeTerms").value("Whisper in game"))
            .andReturn();

        JsonNode body = readBody(result);
        assertThat(body.path("price").path("rate").decimalValue()).isEqualByComparingTo("0.01050000");
        assertThat(body.path("price").path("amount").decimalValue()).isEqualByComparingTo("238.09523810");
    }

    @Test
    void marketOfferDetailsUsesOfferToViewerRateForSellIntent() throws Exception {
        User buyer = createUser("buyer-details-sell");
        long offerId = createActiveOffer(buyer, "buy", "USD", "2.50", "divine-orb", "Buy Divine Orb");

        MvcResult result = mockMvc.perform(get("/api/market/offers/{offerId}", offerId)
                .queryParam("intent", "sell")
                .queryParam("viewerCurrencyCode", "RUB"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(offerId))
            .andExpect(jsonPath("$.side").value("buy"))
            .andExpect(jsonPath("$.action").value("sell"))
            .andExpect(jsonPath("$.price.currencyCode").value("RUB"))
            .andReturn();

        JsonNode body = readBody(result);
        assertThat(body.path("price").path("rate").decimalValue()).isEqualByComparingTo("92.50000000");
        assertThat(body.path("price").path("amount").decimalValue()).isEqualByComparingTo("231.25000000");
    }

    @Test
    void marketOfferDetailsReturns404WhenRateIsMissing() throws Exception {
        User seller = createUser("seller-details-missing-rate");
        long offerId = createActiveOffer(seller, "sell", "KZT", "1000.00", "divine-orb", "No RUB rate");

        mockMvc.perform(get("/api/market/offers/{offerId}", offerId)
                .queryParam("intent", "buy")
                .queryParam("viewerCurrencyCode", "RUB"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MARKET_OFFER_NOT_FOUND"));
    }

    @Test
    void marketOfferDetailsReturns404WhenOfferSideDoesNotMatchIntent() throws Exception {
        User seller = createUser("seller-details-side");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "divine-orb", "Wrong side");

        mockMvc.perform(get("/api/market/offers/{offerId}", offerId)
                .queryParam("intent", "sell")
                .queryParam("viewerCurrencyCode", "RUB"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MARKET_OFFER_NOT_FOUND"));
    }

    @Test
    void marketOfferDetailsReturns404WhenOfferIsNotActive() throws Exception {
        User seller = createUser("seller-details-inactive");
        long offerId = createStoredOffer(
            seller,
            requireCategory("path-of-exile", "currency"),
            "sell",
            "draft",
            "USD",
            "2.50",
            "Draft offer",
            false
        );

        mockMvc.perform(get("/api/market/offers/{offerId}", offerId)
                .queryParam("intent", "buy")
                .queryParam("viewerCurrencyCode", "RUB"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MARKET_OFFER_NOT_FOUND"));
    }

    @Test
    void marketOfferDetailsReturns404WhenCategoryIsUnsupported() throws Exception {
        User seller = createUser("seller-details-items");
        long offerId = createStoredOffer(
            seller,
            requireCategory("path-of-exile", "items"),
            "sell",
            "active",
            "USD",
            "2.50",
            "Item offer",
            true
        );

        mockMvc.perform(get("/api/market/offers/{offerId}", offerId)
                .queryParam("intent", "buy")
                .queryParam("viewerCurrencyCode", "RUB"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MARKET_OFFER_NOT_FOUND"));
    }

    @Test
    void marketOfferDetailsReturns400ForInvalidIntent() throws Exception {
        User seller = createUser("seller-details-invalid-intent");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "divine-orb", "Invalid intent");

        mockMvc.perform(get("/api/market/offers/{offerId}", offerId)
                .queryParam("intent", "hold")
                .queryParam("viewerCurrencyCode", "RUB"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_MARKET_INTENT"));
    }

    @Test
    void marketOfferDetailsReturns400ForInvalidViewerCurrencyCode() throws Exception {
        User seller = createUser("seller-details-invalid-currency");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "divine-orb", "Invalid currency");

        mockMvc.perform(get("/api/market/offers/{offerId}", offerId)
                .queryParam("intent", "buy")
                .queryParam("viewerCurrencyCode", "ZZZ"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_VIEWER_CURRENCY_CODE"));
    }

    private User createUser(String slug) {
        String username = slug;
        if (username.length() > 24) {
            String hash = Integer.toUnsignedString(slug.hashCode(), 36);
            int prefixLength = Math.max(1, 24 - hash.length() - 1);
            username = slug.substring(0, prefixLength) + "-" + hash;
        }
        User user = new User(username, username + "@market.example.test", "password-hash");
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private Category requireCategory(String gameSlug, String categorySlug) {
        return categoryRepository.findActiveByGameSlugAndCategorySlug(gameSlug, categorySlug)
            .orElseThrow();
    }

    private RequestPostProcessor auth(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return authentication(new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        ));
    }

    private long createActiveOffer(
        User owner,
        String side,
        String currencyCode,
        String priceAmount,
        String currencyType,
        String title
    ) throws Exception {
        if ("buy".equals(side)) {
            fundWallet(owner, currencyCode, "1000.0000");
        }
        Category category = requireCategory("path-of-exile", "currency");
        MvcResult result = mockMvc.perform(post("/api/offers")
                .with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(activeOfferRequest(
                    category.getGame().getId(),
                    category.getId(),
                    side,
                    currencyCode,
                    priceAmount,
                    currencyType,
                    title
                )))
            .andExpect(status().isCreated())
            .andReturn();
        return readBody(result).path("id").asLong();
    }

    private void fundWallet(User user, String currencyCode, String amount) {
        UserAccount account = userAccountService.getOrCreateAccount(user.getId(), currencyCode);
        jdbcTemplate.update(
            """
                insert into user_account_txs (user_account_id, amount, type, ref_type, ref_id)
                values (?, ?, ?, ?, ?)
                """,
            account.getId(),
            new BigDecimal(amount),
            "TEST_TOP_UP",
            "TEST",
            testRefIdSequence.getAndIncrement()
        );
    }

    private String activeOfferRequest(
        Long gameId,
        Long categoryId,
        String side,
        String currencyCode,
        String priceAmount,
        String currencyType,
        String title
    ) {
        return """
            {
              "gameId": %d,
              "categoryId": %d,
              "side": "%s",
              "title": "%s",
              "description": "Fast trade",
              "tradeTerms": "Whisper in game",
              "priceCurrencyCode": "%s",
              "priceAmount": %s,
              "quantity": 100,
              "minTradeQuantity": 10,
              "maxTradeQuantity": 50,
              "quantityStep": 1,
              "status": "active",
              "contexts": [
                {"dimensionSlug":"platform","valueSlug":"pc"},
                {"dimensionSlug":"league","valueSlug":"standard"},
                {"dimensionSlug":"mode","valueSlug":"softcore"},
                {"dimensionSlug":"ruthless","valueSlug":"disabled"}
              ],
              "attributes": [
                {"attributeSlug":"currency-type","optionSlug":"%s"}
              ],
              "deliveryMethods": ["f2f", "poe-trade-link"]
            }
            """.formatted(gameId, categoryId, side, title, currencyCode, priceAmount, currencyType);
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long createStoredOffer(
        User owner,
        Category category,
        String side,
        String status,
        String currencyCode,
        String priceAmount,
        String title,
        boolean published
    ) {
        Offer offer = new Offer(owner.getId(), category.getGame().getId(), category.getId(), side, status);
        offer.setTitle(title);
        offer.setDescription("Fast trade");
        offer.setTradeTerms("Whisper in game");
        offer.setPriceCurrencyCode(currencyCode);
        offer.setPriceAmount(new BigDecimal(priceAmount));
        offer.setQuantity(new BigDecimal("100"));
        offer.setMinTradeQuantity(new BigDecimal("10"));
        offer.setMaxTradeQuantity(new BigDecimal("50"));
        offer.setQuantityStep(BigDecimal.ONE);
        if (published) {
            offer.setPublishedAt(Instant.now());
        }
        return offerRepository.saveAndFlush(offer).getId();
    }
}
