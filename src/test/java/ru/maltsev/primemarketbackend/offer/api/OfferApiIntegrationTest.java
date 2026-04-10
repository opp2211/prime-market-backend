package ru.maltsev.primemarketbackend.offer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
import ru.maltsev.primemarketbackend.account.repository.UserAccountRepository;
import ru.maltsev.primemarketbackend.account.service.UserAccountService;
import ru.maltsev.primemarketbackend.category.domain.Category;
import ru.maltsev.primemarketbackend.category.repository.CategoryRepository;
import ru.maltsev.primemarketbackend.offer.domain.Offer;
import ru.maltsev.primemarketbackend.offer.repository.OfferRepository;
import ru.maltsev.primemarketbackend.order.domain.UserAccountHold;
import ru.maltsev.primemarketbackend.order.repository.UserAccountHoldRepository;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.support.AbstractPostgresIntegrationTest;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OfferApiIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfferRepository offerRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private UserAccountHoldRepository userAccountHoldRepository;

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
        jdbcTemplate.update("delete from users where email like ?", "%.example.test");
    }

    @Test
    void listMyOffersReturnsOnlyOffersOfCurrentUser() throws Exception {
        User owner = createUser("owner");
        User stranger = createUser("stranger");

        Offer ownerOffer = createDraftOffer(owner, "Owner offer");
        createDraftOffer(stranger, "Stranger offer");

        mockMvc.perform(get("/api/my/offers").with(auth(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(ownerOffer.getId()))
            .andExpect(jsonPath("$[0].game.slug").value("path-of-exile"))
            .andExpect(jsonPath("$[0].game.title").value("Path of Exile"))
            .andExpect(jsonPath("$[0].category.slug").value("currency"))
            .andExpect(jsonPath("$[0].category.title").value("Currency"));
    }

    @Test
    void getOfferIsOwnerOnly() throws Exception {
        User owner = createUser("owner");
        User stranger = createUser("stranger");
        Offer ownerOffer = createDraftOffer(owner, "Owner offer");

        mockMvc.perform(get("/api/offers/{offerId}", ownerOffer.getId()).with(auth(stranger)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
    }

    @Test
    void createActiveOfferReturnsEnrichedResponse() throws Exception {
        User owner = createUser("owner");
        Category category = requireCategory("path-of-exile", "currency");

        MvcResult result = mockMvc.perform(post("/api/offers")
                .with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(activeOfferRequest(category.getGame().getId(), category.getId(), "sell", "USD", "2.50")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.game.id").value(category.getGame().getId()))
            .andExpect(jsonPath("$.game.slug").value("path-of-exile"))
            .andExpect(jsonPath("$.category.id").value(category.getId()))
            .andExpect(jsonPath("$.category.slug").value("currency"))
            .andExpect(jsonPath("$.publishedAt").isNotEmpty())
            .andReturn();

        JsonNode body = readBody(result);
        assertThat(body.path("gameId").asLong()).isEqualTo(category.getGame().getId());
        assertThat(body.path("categoryId").asLong()).isEqualTo(category.getId());
    }

    @Test
    void createActiveOfferWithMissingRequiredDeliveryMethodsReturns400() throws Exception {
        User owner = createUser("owner");
        Category category = requireCategory("path-of-exile", "currency");

        mockMvc.perform(post("/api/offers")
                .with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "gameId": %d,
                      "categoryId": %d,
                      "side": "sell",
                      "title": "Invalid active offer",
                      "priceCurrencyCode": "USD",
                      "priceAmount": 2.5,
                      "quantity": 100,
                      "status": "active",
                      "contexts": [
                        {"dimensionSlug":"platform","valueSlug":"pc"},
                        {"dimensionSlug":"league","valueSlug":"standard"},
                        {"dimensionSlug":"mode","valueSlug":"softcore"},
                        {"dimensionSlug":"ruthless","valueSlug":"disabled"}
                      ],
                      "attributes": [
                        {"attributeSlug":"currency-type","optionSlug":"divine-orb"}
                      ],
                      "deliveryMethods": []
                    }
                    """.formatted(category.getGame().getId(), category.getId())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DELIVERY_METHODS_REQUIRED"));
    }

    @Test
    void createActiveBuyOfferRequiresFunding() throws Exception {
        User owner = createUser("buy-owner-no-funds");
        Category category = requireCategory("path-of-exile", "currency");

        mockMvc.perform(post("/api/offers")
                .with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(activeOfferRequest(category.getGame().getId(), category.getId(), "buy", "USD", "2.50")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS_FOR_BUY_OFFER"));
    }

    @Test
    void createActiveBuyOfferCreatesOfferLevelFundingHold() throws Exception {
        User owner = createUser("buy-owner-funded");
        Category category = requireCategory("path-of-exile", "currency");
        fundWallet(owner, "USD", "500.0000");

        JsonNode created = createActiveOffer(owner, category, "buy", "USD", "2.50");

        UserAccountHold hold = userAccountHoldRepository.findByRef("offer", created.path("id").asLong(), "buy_offer_funds_hold")
            .orElseThrow();
        UserAccount wallet = userAccountRepository.findByUserIdAndCurrencyCode(owner.getId(), "USD").orElseThrow();
        assertThat(hold.getStatus()).isEqualTo("active");
        assertThat(hold.getAmount()).isEqualByComparingTo("250.0000");
        assertThat(hold.getRefType()).isEqualTo("offer");
        assertThat(hold.getRefId()).isEqualTo(created.path("id").asLong());
        assertThat(wallet.getReserved()).isEqualByComparingTo("250.0000");
    }

    @Test
    void patchCanPauseActiveOffer() throws Exception {
        User owner = createUser("owner");
        Category category = requireCategory("path-of-exile", "currency");
        long offerId = createActiveOffer(owner, category, "sell", "USD", "2.50").path("id").asLong();

        mockMvc.perform(patch("/api/offers/{offerId}", offerId)
                .with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "paused"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("paused"))
            .andExpect(jsonPath("$.publishedAt").isNotEmpty());
    }

    @Test
    void patchCanRepublishPausedOfferWhenStateIsStillValid() throws Exception {
        User owner = createUser("owner");
        Category category = requireCategory("path-of-exile", "currency");
        JsonNode created = createActiveOffer(owner, category, "sell", "USD", "2.50");
        long offerId = created.path("id").asLong();
        String firstPublishedAt = created.path("publishedAt").asText();

        mockMvc.perform(patch("/api/offers/{offerId}", offerId)
                .with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "paused"
                    }
                    """))
            .andExpect(status().isOk());

        Thread.sleep(5L);

        MvcResult republish = mockMvc.perform(patch("/api/offers/{offerId}", offerId)
                .with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "active"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("active"))
            .andReturn();

        JsonNode body = readBody(republish);
        assertThat(body.path("publishedAt").asText()).isNotEqualTo(firstPublishedAt);
    }

    @Test
    void patchRejectsRepublishWhenOfferBecomesInvalid() throws Exception {
        User owner = createUser("owner");
        Category category = requireCategory("path-of-exile", "currency");
        long offerId = createActiveOffer(owner, category, "sell", "USD", "2.50").path("id").asLong();

        mockMvc.perform(patch("/api/offers/{offerId}", offerId)
                .with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "paused"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/offers/{offerId}", offerId)
                .with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "active",
                      "priceAmount": null
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PRICE_AMOUNT_REQUIRED"));
    }

    @Test
    void patchActiveBuyOfferRecalculatesFundingHold() throws Exception {
        User owner = createUser("buy-owner-reprice");
        Category category = requireCategory("path-of-exile", "currency");
        fundWallet(owner, "USD", "1000.0000");
        long offerId = createActiveOffer(owner, category, "buy", "USD", "2.50").path("id").asLong();

        mockMvc.perform(patch("/api/offers/{offerId}", offerId)
                .with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "priceAmount": 3.00,
                      "quantity": 120
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceAmount").value(3.00))
            .andExpect(jsonPath("$.quantity").value(120));

        UserAccountHold hold = userAccountHoldRepository.findByRef("offer", offerId, "buy_offer_funds_hold").orElseThrow();
        UserAccount wallet = userAccountRepository.findByUserIdAndCurrencyCode(owner.getId(), "USD").orElseThrow();
        assertThat(hold.getAmount()).isEqualByComparingTo("360.0000");
        assertThat(wallet.getReserved()).isEqualByComparingTo("360.0000");
    }

    @Test
    void patchActiveBuyOfferRejectsIncreaseWhenFundingIsInsufficient() throws Exception {
        User owner = createUser("buy-owner-insufficient");
        Category category = requireCategory("path-of-exile", "currency");
        fundWallet(owner, "USD", "300.0000");
        long offerId = createActiveOffer(owner, category, "buy", "USD", "2.50").path("id").asLong();

        mockMvc.perform(patch("/api/offers/{offerId}", offerId)
                .with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "quantity": 150
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS_FOR_BUY_OFFER"));

        UserAccountHold hold = userAccountHoldRepository.findByRef("offer", offerId, "buy_offer_funds_hold").orElseThrow();
        UserAccount wallet = userAccountRepository.findByUserIdAndCurrencyCode(owner.getId(), "USD").orElseThrow();
        assertThat(hold.getAmount()).isEqualByComparingTo("250.0000");
        assertThat(wallet.getReserved()).isEqualByComparingTo("250.0000");
    }

    @Test
    void patchPausesActiveBuyOfferAndReleasesUnallocatedFunding() throws Exception {
        User owner = createUser("buy-owner-pause");
        Category category = requireCategory("path-of-exile", "currency");
        fundWallet(owner, "USD", "500.0000");
        long offerId = createActiveOffer(owner, category, "buy", "USD", "2.50").path("id").asLong();

        mockMvc.perform(patch("/api/offers/{offerId}", offerId)
                .with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "paused"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("paused"));

        UserAccountHold hold = userAccountHoldRepository.findByRef("offer", offerId, "buy_offer_funds_hold").orElseThrow();
        UserAccount wallet = userAccountRepository.findByUserIdAndCurrencyCode(owner.getId(), "USD").orElseThrow();
        assertThat(hold.getStatus()).isEqualTo("released");
        assertThat(wallet.getReserved()).isEqualByComparingTo("0.0000");
    }

    @Test
    void offerSchemaIsPublicAndReturnsFrontendFriendlyContract() throws Exception {
        mockMvc.perform(get("/api/games/path-of-exile/categories/currency/offer-schema"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contexts[0].slug").value("platform"))
            .andExpect(jsonPath("$.contexts[0].defaultValue.slug").value("pc"))
            .andExpect(jsonPath("$.attributes[0].slug").value("currency-type"))
            .andExpect(jsonPath("$.attributes[0].dataType").value("select"))
            .andExpect(jsonPath("$.tradeFields[0].fieldSlug").value("quantity"))
            .andExpect(jsonPath("$.tradeFields[0].title").value("Quantity"))
            .andExpect(jsonPath("$.tradeFields[0].dataType").value("number"))
            .andExpect(jsonPath("$.deliveryMethods[0].slug").isNotEmpty())
            .andExpect(jsonPath("$.deliveryMethods[0].title").isNotEmpty());
    }

    @Test
    void patchCanClearNullableFieldsInDraftOffer() throws Exception {
        User owner = createUser("owner");
        Offer offer = createDraftOffer(owner, "Draft offer");
        offer.setDescription("To clear");
        offer.setTradeTerms("To clear");
        offer.setPriceCurrencyCode("USD");
        offer.setPriceAmount(new BigDecimal("2.50"));
        offer.setQuantity(new BigDecimal("10"));
        offer.setMinTradeQuantity(new BigDecimal("2"));
        offer.setMaxTradeQuantity(new BigDecimal("8"));
        offer.setQuantityStep(new BigDecimal("2"));
        offerRepository.saveAndFlush(offer);

        mockMvc.perform(patch("/api/offers/{offerId}", offer.getId())
                .with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": null,
                      "description": null,
                      "tradeTerms": null,
                      "priceCurrencyCode": null,
                      "priceAmount": null,
                      "quantity": null,
                      "minTradeQuantity": null,
                      "maxTradeQuantity": null,
                      "quantityStep": null
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").isEmpty())
            .andExpect(jsonPath("$.description").isEmpty())
            .andExpect(jsonPath("$.tradeTerms").isEmpty())
            .andExpect(jsonPath("$.priceCurrencyCode").isEmpty())
            .andExpect(jsonPath("$.priceAmount").isEmpty())
            .andExpect(jsonPath("$.quantity").isEmpty())
            .andExpect(jsonPath("$.minTradeQuantity").isEmpty())
            .andExpect(jsonPath("$.maxTradeQuantity").isEmpty())
            .andExpect(jsonPath("$.quantityStep").isEmpty());
    }

    @Test
    void publicGamesAndCategoriesEndpointsAreAccessibleWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/games"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].slug").isNotEmpty());

        mockMvc.perform(get("/api/games/path-of-exile/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].slug").isNotEmpty());
    }

    private User createUser(String slug) {
        User user = new User(slug, slug + "@offer.example.test", "password-hash");
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private Offer createDraftOffer(User user, String title) {
        Category category = requireCategory("path-of-exile", "currency");
        Offer offer = new Offer(user.getId(), category.getGame().getId(), category.getId(), "sell", "draft");
        offer.setTitle(title);
        return offerRepository.saveAndFlush(offer);
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

    private JsonNode createActiveOffer(
        User owner,
        Category category,
        String side,
        String currencyCode,
        String priceAmount
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/offers")
                .with(auth(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(activeOfferRequest(category.getGame().getId(), category.getId(), side, currencyCode, priceAmount)))
            .andExpect(status().isCreated())
            .andReturn();
        return readBody(result);
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
        String priceAmount
    ) {
        return """
            {
              "gameId": %d,
              "categoryId": %d,
              "side": "%s",
              "title": "Divine Orbs bulk",
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
                {"attributeSlug":"currency-type","optionSlug":"divine-orb"}
              ],
              "deliveryMethods": ["f2f", "poe-trade-link"]
            }
            """.formatted(gameId, categoryId, side, currencyCode, priceAmount);
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
