package ru.maltsev.primemarketbackend.orderquote.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
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
import ru.maltsev.primemarketbackend.offer.repository.OfferRepository;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.support.AbstractPostgresIntegrationTest;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OrderQuoteApiIntegrationTest extends AbstractPostgresIntegrationTest {
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
        jdbcTemplate.update("delete from users where email like ?", "%.quote.example.test");
        jdbcTemplate.update(
            "update currency_rates set rate = ? where from_currency_code = ? and to_currency_code = ?",
            new BigDecimal("0.01050000"),
            "RUB",
            "USD"
        );
        jdbcTemplate.update(
            "update currency_rates set rate = ? where from_currency_code = ? and to_currency_code = ?",
            new BigDecimal("92.50000000"),
            "USD",
            "RUB"
        );
    }

    @Test
    void createQuoteForBuyUsesCorrectConversionDirection() throws Exception {
        User seller = createUser("quote-buy-seller");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "divine-orb", "Sell Divine Orb");

        mockMvc.perform(post("/api/market/offers/{offerId}/quote", offerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createQuoteRequest("buy", "RUB", 1L, "238.09523810")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.side").value("sell"))
            .andExpect(jsonPath("$.action").value("buy"))
            .andExpect(jsonPath("$.offerVersion").value(1))
            .andExpect(jsonPath("$.price.amount").value(238.09523810))
            .andExpect(jsonPath("$.price.currencyCode").value("RUB"))
            .andExpect(jsonPath("$.price.rate").value(0.01050000))
            .andExpect(jsonPath("$.priceChanged").value(false))
            .andExpect(jsonPath("$.offerUpdated").value(false));
    }

    @Test
    void createQuoteForSellUsesCorrectConversionDirection() throws Exception {
        User buyer = createUser("quote-sell-buyer");
        long offerId = createActiveOffer(buyer, "buy", "USD", "2.50", "divine-orb", "Buy Divine Orb");

        mockMvc.perform(post("/api/market/offers/{offerId}/quote", offerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createQuoteRequest("sell", "RUB", 1L, "231.25000000")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.side").value("buy"))
            .andExpect(jsonPath("$.action").value("sell"))
            .andExpect(jsonPath("$.price.amount").value(231.25000000))
            .andExpect(jsonPath("$.price.currencyCode").value("RUB"))
            .andExpect(jsonPath("$.price.rate").value(92.50000000))
            .andExpect(jsonPath("$.priceChanged").value(false))
            .andExpect(jsonPath("$.offerUpdated").value(false));
    }

    @Test
    void createQuoteMarksPriceChangedWhenListedUnitPriceAmountDiffers() throws Exception {
        User seller = createUser("quote-price-changed");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "divine-orb", "Changed price");

        mockMvc.perform(post("/api/market/offers/{offerId}/quote", offerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createQuoteRequest("buy", "RUB", 1L, "240.00000000")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceChanged").value(true))
            .andExpect(jsonPath("$.offerUpdated").value(false));
    }

    @Test
    void createQuoteMarksOfferUpdatedWhenListedOfferVersionDiffers() throws Exception {
        User seller = createUser("quote-offer-updated");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "divine-orb", "Changed offer");

        mockMvc.perform(post("/api/market/offers/{offerId}/quote", offerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createQuoteRequest("buy", "RUB", 0L, "238.09523810")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceChanged").value(false))
            .andExpect(jsonPath("$.offerUpdated").value(true));
    }

    @Test
    void createQuoteUnavailableWhenRateMissing() throws Exception {
        User seller = createUser("quote-missing-rate");
        long offerId = createActiveOffer(seller, "sell", "KZT", "1000.00", "divine-orb", "No rate");

        mockMvc.perform(post("/api/market/offers/{offerId}/quote", offerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createQuoteRequest("buy", "RUB", 1L, "1.00000000")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OFFER_UNAVAILABLE"));
    }

    @Test
    void refreshQuoteReturnsNewQuoteWithNewExpiresAt() throws Exception {
        User seller = createUser("quote-refresh");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "divine-orb", "Refreshable offer");
        JsonNode createdQuote = createQuote(offerId, "buy", "RUB", 1L, "238.09523810");

        Thread.sleep(5L);
        expireQuote(UUID.fromString(createdQuote.path("quoteId").asText()));

        JsonNode refreshed = refreshQuote(createdQuote.path("quoteId").asText());
        assertThat(refreshed.path("quoteId").asText()).isNotEqualTo(createdQuote.path("quoteId").asText());
        assertThat(Instant.parse(refreshed.path("expiresAt").asText()))
            .isAfter(Instant.parse(createdQuote.path("expiresAt").asText()));
        assertThat(refreshed.path("priceChanged").asBoolean()).isFalse();
        assertThat(refreshed.path("offerUpdated").asBoolean()).isFalse();
    }

    @Test
    void refreshQuoteCanDistinguishFxOnlyChangeVsOfferUpdate() throws Exception {
        User seller = createUser("quote-refresh-flags");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "divine-orb", "Initial title");
        JsonNode firstQuote = createQuote(offerId, "buy", "RUB", 1L, "238.09523810");

        expireQuote(UUID.fromString(firstQuote.path("quoteId").asText()));
        jdbcTemplate.update(
            "update currency_rates set rate = ? where from_currency_code = ? and to_currency_code = ?",
            new BigDecimal("0.01000000"),
            "RUB",
            "USD"
        );

        JsonNode secondQuote = refreshQuote(firstQuote.path("quoteId").asText());
        assertThat(secondQuote.path("priceChanged").asBoolean()).isTrue();
        assertThat(secondQuote.path("offerUpdated").asBoolean()).isFalse();
        assertThat(secondQuote.path("price").path("amount").decimalValue()).isEqualByComparingTo("250.00000000");

        expireQuote(UUID.fromString(secondQuote.path("quoteId").asText()));
        mockMvc.perform(patch("/api/offers/{offerId}", offerId)
                .with(auth(seller))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Updated title"
                    }
                    """))
            .andExpect(status().isOk());

        JsonNode thirdQuote = refreshQuote(secondQuote.path("quoteId").asText());
        assertThat(thirdQuote.path("priceChanged").asBoolean()).isFalse();
        assertThat(thirdQuote.path("offerUpdated").asBoolean()).isTrue();
        assertThat(thirdQuote.path("title").asText()).isEqualTo("Updated title");
    }

    @Test
    void refreshQuoteReturnsConflictWhenOfferBecameUnavailable() throws Exception {
        User seller = createUser("quote-refresh-unavailable");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "divine-orb", "Unavailable later");
        JsonNode createdQuote = createQuote(offerId, "buy", "RUB", 1L, "238.09523810");
        UUID quoteId = UUID.fromString(createdQuote.path("quoteId").asText());

        expireQuote(quoteId);
        mockMvc.perform(patch("/api/offers/{offerId}", offerId)
                .with(auth(seller))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "paused"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/order-quotes/{quoteId}/refresh", quoteId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OFFER_UNAVAILABLE"));

        String storedStatus = jdbcTemplate.queryForObject(
            "select status from order_quotes where public_id = ?",
            String.class,
            quoteId
        );
        assertThat(storedStatus).isEqualTo("expired");
    }

    private User createUser(String slug) {
        String username = slug;
        if (username.length() > 24) {
            String hash = Integer.toUnsignedString(slug.hashCode(), 36);
            int prefixLength = Math.max(1, 24 - hash.length() - 1);
            username = slug.substring(0, prefixLength) + "-" + hash;
        }
        User user = new User(username, username + "@quote.example.test", "password-hash");
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

    private JsonNode createQuote(
        long offerId,
        String intent,
        String viewerCurrencyCode,
        long listedOfferVersion,
        String listedUnitPriceAmount
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/market/offers/{offerId}/quote", offerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createQuoteRequest(intent, viewerCurrencyCode, listedOfferVersion, listedUnitPriceAmount)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode refreshQuote(String quoteId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/order-quotes/{quoteId}/refresh", quoteId))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private void expireQuote(UUID quoteId) {
        jdbcTemplate.update(
            "update order_quotes set expires_at = ? where public_id = ?",
            Timestamp.from(Instant.now().minusSeconds(1)),
            quoteId
        );
    }

    private String createQuoteRequest(
        String intent,
        String viewerCurrencyCode,
        long listedOfferVersion,
        String listedUnitPriceAmount
    ) {
        return """
            {
              "intent": "%s",
              "viewerCurrencyCode": "%s",
              "listedOfferVersion": %d,
              "listedUnitPriceAmount": %s
            }
            """.formatted(intent, viewerCurrencyCode, listedOfferVersion, listedUnitPriceAmount);
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
}
