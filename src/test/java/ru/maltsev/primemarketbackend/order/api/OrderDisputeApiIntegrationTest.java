package ru.maltsev.primemarketbackend.order.api;

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
import java.util.List;
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
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.support.AbstractPostgresIntegrationTest;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OrderDisputeApiIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final String BUY_QUOTE_DISPLAY_PRICE = "238.09523810";
    private static final String SUPPORT_JOINED_MESSAGE = "Поддержка подключилась к заказу";

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
                order_messages,
                order_conversation_participants,
                order_conversations,
                order_disputes,
                order_events,
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
        jdbcTemplate.update("delete from users where email like ?", "%.dispute.example.test");
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
    void buyerAndSellerCanOpenDisputesButSecondActiveDisputeIsRejected() throws Exception {
        User sellerOne = createUser("dispute-open-seller-1");
        User buyerOne = createUser("dispute-open-buyer-1");
        fundWallet(buyerOne, "RUB", "50000.0000");

        JsonNode firstOrder = createPendingSellOrder(sellerOne, buyerOne, "20", "Buyer dispute order");
        confirmReady(sellerOne, firstOrder.path("publicId").asText());

        JsonNode buyerDispute = openDispute(
            buyerOne,
            firstOrder.path("publicId").asText(),
            "item_not_received",
            "Buyer did not receive the item"
        );
        assertThat(buyerDispute.path("status").asText()).isEqualTo("open");
        assertThat(buyerDispute.path("openedByRole").asText()).isEqualTo("buyer");

        JsonNode fetchedBuyerDispute = getOrderDispute(buyerOne, firstOrder.path("publicId").asText());
        assertThat(fetchedBuyerDispute.path("publicId").asText()).isEqualTo(buyerDispute.path("publicId").asText());

        mockMvc.perform(post("/api/orders/{orderId}/disputes", firstOrder.path("publicId").asText())
                .with(auth(sellerOne))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reasonCode": "delivery_issue",
                      "description": "Second active dispute should fail"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_DISPUTE_ALREADY_ACTIVE"));

        User sellerTwo = createUser("dispute-open-seller-2");
        User buyerTwo = createUser("dispute-open-buyer-2");
        fundWallet(buyerTwo, "RUB", "50000.0000");

        JsonNode secondOrder = createPendingSellOrder(sellerTwo, buyerTwo, "20", "Seller dispute order");
        confirmReady(sellerTwo, secondOrder.path("publicId").asText());

        JsonNode sellerDispute = openDispute(
            sellerTwo,
            secondOrder.path("publicId").asText(),
            "buyer_unresponsive",
            "Seller asked support to review the order"
        );
        assertThat(sellerDispute.path("status").asText()).isEqualTo("open");
        assertThat(sellerDispute.path("openedByRole").asText()).isEqualTo("seller");
    }

    @Test
    void supportPassiveAccessCanViewDisputedOrderAndChatsWithoutAssignmentOrJoinMessage() throws Exception {
        User seller = createUser("passive-seller");
        User buyer = createUser("passive-buyer");
        User support = loadUserWithRoles("sup1@123.123");
        fundWallet(buyer, "RUB", "50000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20", "Passive support access");
        confirmReady(seller, order.path("publicId").asText());
        openDispute(buyer, order.path("publicId").asText(), "item_not_received", "Need support review");

        JsonNode details = getOrderDetails(support, order.path("publicId").asText());
        assertThat(details.path("dispute").path("exists").asBoolean()).isTrue();
        assertThat(details.path("dispute").path("status").asText()).isEqualTo("open");
        assertThat(details.path("dispute").path("availableActions").path("canTakeInWork").asBoolean()).isTrue();

        JsonNode conversations = getOrderConversations(support, order.path("publicId").asText());
        assertThat(conversations.path("items")).hasSize(3);
        assertThat(findConversationByType(conversations, "order_main").path("publicId").asText()).isNotBlank();
        assertThat(findConversationByType(conversations, "order_support_buyer").path("publicId").asText()).isNotBlank();
        assertThat(findConversationByType(conversations, "order_support_seller").path("publicId").asText()).isNotBlank();

        JsonNode events = getOrderEvents(support, order.path("publicId").asText());
        assertThat(events.path("items"))
            .extracting(node -> node.path("eventType").asText())
            .contains("dispute_opened");

        StoredDispute storedDispute = loadLatestDispute(order.path("id").asLong());
        assertThat(storedDispute.status()).isEqualTo("open");
        assertThat(storedDispute.assignedSupportUserId()).isNull();
        assertThat(storedDispute.takenAt()).isNull();
        assertThat(loadSystemMessageCount(order.path("id").asLong(), SUPPORT_JOINED_MESSAGE)).isZero();
    }

    @Test
    void supportCanTakeOpenDisputeInWork() throws Exception {
        User seller = createUser("take-seller");
        User buyer = createUser("take-buyer");
        User support = loadUserWithRoles("sup1@123.123");
        fundWallet(buyer, "RUB", "50000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20", "Take dispute");
        confirmReady(seller, order.path("publicId").asText());
        JsonNode dispute = openDispute(buyer, order.path("publicId").asText(), "item_not_received", "Take in work");

        JsonNode taken = takeDispute(support, dispute.path("publicId").asText());
        assertThat(taken.path("status").asText()).isEqualTo("in_review");
        assertThat(taken.path("assignedSupportUser").path("id").asLong()).isEqualTo(support.getId());

        StoredDispute storedDispute = loadLatestDispute(order.path("id").asLong());
        assertThat(storedDispute.status()).isEqualTo("in_review");
        assertThat(storedDispute.assignedSupportUserId()).isEqualTo(support.getId());
        assertThat(storedDispute.takenAt()).isNotNull();
        assertThat(loadConversationParticipantCount(order.path("id").asLong(), "order_main")).isEqualTo(3);
        assertThat(loadSystemMessageCount(order.path("id").asLong(), SUPPORT_JOINED_MESSAGE)).isEqualTo(1);

        JsonNode mainMessages = getConversationMessages(
            support,
            loadConversationPublicId(order.path("id").asLong(), "order_main")
        );
        assertThat(mainMessages.path("items"))
            .extracting(node -> node.path("body").asText())
            .contains(SUPPORT_JOINED_MESSAGE);
    }

    @Test
    void supportCanForceCancelActiveDispute() throws Exception {
        User seller = createUser("cancel-seller");
        User buyer = createUser("cancel-buyer");
        User support = loadUserWithRoles("sup1@123.123");
        fundWallet(buyer, "RUB", "50000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20", "Force cancel");
        confirmReady(seller, order.path("publicId").asText());
        JsonNode dispute = openDispute(buyer, order.path("publicId").asText(), "item_not_received", "Cancel this order");

        JsonNode resolved = resolveCancel(support, dispute.path("publicId").asText(), "Support canceled the order");
        assertThat(resolved.path("status").asText()).isEqualTo("resolved");
        assertThat(resolved.path("resolutionType").asText()).isEqualTo("force_cancel");
        assertThat(loadOrderStatus(order.path("publicId").asText())).isEqualTo("canceled");
        assertThat(loadHoldStatus(order.path("id").asLong())).isEqualTo("released");
        assertThat(loadReservationStatus(order.path("id").asLong())).isEqualTo("released");
        assertThat(loadReservedAmount(buyer.getId(), "RUB")).isEqualByComparingTo("0.0000");
        assertThat(loadOrderEventTypes(order.path("id").asLong()))
            .contains("dispute_resolved", "order_force_canceled_by_support");
    }

    @Test
    void supportCanForceCompleteWithoutDuplicateSettlement() throws Exception {
        User seller = createUser("complete-seller");
        User buyer = createUser("complete-buyer");
        User support = loadUserWithRoles("sup1@123.123");
        fundWallet(buyer, "RUB", "50000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20", "Force complete");
        confirmReady(seller, order.path("publicId").asText());
        markDelivered(seller, order.path("publicId").asText());
        JsonNode dispute = openDispute(buyer, order.path("publicId").asText(), "delivery_confirmed", "Support should complete");

        JsonNode resolved = resolveComplete(support, dispute.path("publicId").asText(), "Support completed the order");
        assertThat(resolved.path("status").asText()).isEqualTo("resolved");
        assertThat(resolved.path("resolutionType").asText()).isEqualTo("force_complete");
        assertThat(loadOrderStatus(order.path("publicId").asText())).isEqualTo("completed");
        assertThat(loadLatestDispute(order.path("id").asLong()).status()).isEqualTo("resolved");

        long orderId = order.path("id").asLong();
        assertThat(countUserTxs("ORDER_BUYER_SETTLEMENT", orderId)).isEqualTo(1);
        assertThat(countUserTxs("ORDER_SELLER_PAYOUT", orderId)).isEqualTo(1);
        assertThat(countPlatformTxs("ORDER_PLATFORM_FEE", orderId)).isEqualTo(1);
        assertThat(loadUserTxAmount(buyer.getId(), "RUB", "ORDER_BUYER_SETTLEMENT", orderId))
            .isEqualByComparingTo("-4761.9048");
        assertThat(loadUserTxAmount(seller.getId(), "USD", "ORDER_SELLER_PAYOUT", orderId))
            .isEqualByComparingTo("49.5000");
        assertThat(loadPlatformTxAmount("USD", "ORDER_PLATFORM_FEE", orderId))
            .isEqualByComparingTo("0.5000");
        assertThat(loadOrderEventTypes(orderId))
            .contains("dispute_resolved", "order_force_completed_by_support");
    }

    @Test
    void supportCanForceAmendQuantityAndCompleteWithRebalancedReserves() throws Exception {
        User seller = createUser("amend-seller");
        User buyer = createUser("amend-buyer");
        User support = loadUserWithRoles("sup1@123.123");
        fundWallet(buyer, "RUB", "50000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "100", "Force amend and complete");
        confirmReady(seller, order.path("publicId").asText());
        markPartiallyDelivered(seller, order.path("publicId").asText(), "65");
        JsonNode dispute = openDispute(
            buyer,
            order.path("publicId").asText(),
            "quantity_mismatch",
            "Support should set final quantity"
        );

        JsonNode resolved = resolveAmendQuantityAndComplete(
            support,
            dispute.path("publicId").asText(),
            "65",
            "Support established final delivered quantity"
        );
        assertThat(resolved.path("status").asText()).isEqualTo("resolved");
        assertThat(resolved.path("resolutionType").asText())
            .isEqualTo("force_amend_quantity_and_complete");
        assertThat(loadOrderStatus(order.path("publicId").asText())).isEqualTo("completed");
        assertThat(loadOrderedQuantity(order.path("publicId").asText())).isEqualByComparingTo("65.00000000");
        assertThat(loadHoldStatus(order.path("id").asLong())).isEqualTo("consumed");
        assertThat(loadHoldAmount(order.path("id").asLong())).isEqualByComparingTo("15476.1905");
        assertThat(loadReservedAmount(buyer.getId(), "RUB")).isEqualByComparingTo("0.0000");
        assertThat(loadUserTxAmount(seller.getId(), "USD", "ORDER_SELLER_PAYOUT", order.path("id").asLong()))
            .isEqualByComparingTo("160.8750");
        assertThat(loadPlatformTxAmount("USD", "ORDER_PLATFORM_FEE", order.path("id").asLong()))
            .isEqualByComparingTo("1.6250");
        assertThat(loadOrderEventTypes(order.path("id").asLong()))
            .contains(
                "dispute_resolved",
                "order_force_amended_quantity_by_support",
                "order_force_completed_by_support"
            );
    }

    @Test
    void permissionChecksSeparateBackofficeOrderAccessAndChatAccess() throws Exception {
        User seller = createUser("perm-seller");
        User buyer = createUser("perm-buyer");
        User roleOnly = createUser("perm-role-only");
        User viewAny = createUser("perm-view-any");
        User chatView = createUser("perm-chat-view");
        fundWallet(buyer, "RUB", "50000.0000");

        assignRoleWithPermissions(roleOnly, "ROLE_ONLY_AUDITOR");
        assignRoleWithPermissions(viewAny, "ORDER_VIEWER", "ORDERS_VIEW_ANY");
        assignRoleWithPermissions(chatView, "CHAT_VIEWER", "ORDER_CHATS_VIEW_ANY");

        roleOnly = loadUserWithRoles(roleOnly.getEmail());
        viewAny = loadUserWithRoles(viewAny.getEmail());
        chatView = loadUserWithRoles(chatView.getEmail());

        JsonNode order = createPendingSellOrder(seller, buyer, "20", "Permission separation");
        confirmReady(seller, order.path("publicId").asText());
        openDispute(buyer, order.path("publicId").asText(), "item_not_received", "Permission checks");

        mockMvc.perform(get("/api/backoffice/disputes").with(auth(loadUserWithRoles("user1@123.123"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/backoffice/disputes").with(auth(roleOnly)))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/{orderId}", order.path("publicId").asText()).with(auth(roleOnly)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders/{orderId}", order.path("publicId").asText()).with(auth(viewAny)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dispute.exists").value(false));
        mockMvc.perform(get("/api/orders/{orderId}/conversations", order.path("publicId").asText()).with(auth(viewAny)))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/orders/{orderId}", order.path("publicId").asText()).with(auth(chatView)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders/{orderId}/conversations", order.path("publicId").asText()).with(auth(chatView)))
            .andExpect(status().isOk());

        JsonNode backoffice = getBackofficeDisputes(loadUserWithRoles("sup1@123.123"));
        assertThat(backoffice.path("open")).hasSize(1);
    }

    private User createUser(String slug) {
        String username = slug;
        if (username.length() > 24) {
            String hash = Integer.toUnsignedString(slug.hashCode(), 36);
            int prefixLength = Math.max(1, 24 - hash.length() - 1);
            username = slug.substring(0, prefixLength) + "-" + hash;
        }
        User user = new User(username, username + "@dispute.example.test", "password-hash");
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private User loadUserWithRoles(String email) {
        return userRepository.findWithRolesByEmailIgnoreCase(email)
            .orElseThrow(() -> new IllegalStateException("User not found: " + email));
    }

    private void assignRoleWithPermissions(User user, String roleCode, String... permissionCodes) {
        Long roleId = jdbcTemplate.queryForObject(
            """
                insert into roles(code)
                values (?)
                on conflict (code) do update set code = excluded.code
                returning id
                """,
            Long.class,
            roleCode
        );
        jdbcTemplate.update(
            """
                insert into user_roles(user_id, role_id)
                values (?, ?)
                on conflict do nothing
                """,
            user.getId(),
            roleId
        );
        for (String permissionCode : permissionCodes) {
            Long permissionId = jdbcTemplate.queryForObject(
                "select id from permissions where code = ?",
                Long.class,
                permissionCode
            );
            jdbcTemplate.update(
                """
                    insert into role_permissions(role_id, permission_id)
                    values (?, ?)
                    on conflict do nothing
                    """,
                roleId,
                permissionId
            );
        }
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

    private JsonNode createPendingSellOrder(User seller, User buyer, String quantity, String title) throws Exception {
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", title);
        JsonNode quote = createQuote(offerId, "buy", "RUB", 1L, BUY_QUOTE_DISPLAY_PRICE);
        return createOrder(buyer, quote.path("quoteId").asText(), quantity);
    }

    private long createActiveOffer(
        User owner,
        String side,
        String currencyCode,
        String priceAmount,
        String title
    ) throws Exception {
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
                    title
                )))
            .andExpect(status().isCreated())
            .andReturn();
        return readBody(result).path("id").asLong();
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

    private JsonNode createOrder(User taker, String quoteId, String quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                .with(auth(taker))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createOrderRequest(quoteId, quantity)))
            .andExpect(status().isCreated())
            .andReturn();
        return readBody(result);
    }

    private JsonNode confirmReady(User user, String orderPublicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/confirm-ready", orderPublicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode markDelivered(User user, String orderPublicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/mark-delivered", orderPublicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode markPartiallyDelivered(User user, String orderPublicId, String deliveredQuantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/mark-partially-delivered", orderPublicId)
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "deliveredQuantity": %s
                    }
                    """.formatted(deliveredQuantity)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode openDispute(
        User user,
        String orderPublicId,
        String reasonCode,
        String description
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/disputes", orderPublicId)
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reasonCode": "%s",
                      "description": "%s"
                    }
                    """.formatted(reasonCode, description)))
            .andExpect(status().isCreated())
            .andReturn();
        return readBody(result);
    }

    private JsonNode getOrderDispute(User user, String orderPublicId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/orders/{orderId}/dispute", orderPublicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode takeDispute(User user, String disputePublicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/order-disputes/{disputeId}/take", disputePublicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode resolveCancel(User user, String disputePublicId, String note) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/order-disputes/{disputeId}/resolve-cancel", disputePublicId)
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "note": "%s"
                    }
                    """.formatted(note)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode resolveComplete(User user, String disputePublicId, String note) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/order-disputes/{disputeId}/resolve-complete", disputePublicId)
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "note": "%s"
                    }
                    """.formatted(note)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode resolveAmendQuantityAndComplete(
        User user,
        String disputePublicId,
        String quantity,
        String note
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                post("/api/order-disputes/{disputeId}/resolve-amend-quantity-and-complete", disputePublicId)
                    .with(auth(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "quantity": %s,
                          "note": "%s"
                        }
                        """.formatted(quantity, note)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode getOrderDetails(User user, String orderPublicId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/orders/{orderId}", orderPublicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode getOrderEvents(User user, String orderPublicId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/orders/{orderId}/events", orderPublicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode getOrderConversations(User user, String orderPublicId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/orders/{orderId}/conversations", orderPublicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode getConversationMessages(User user, String conversationPublicId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/order-conversations/{conversationId}/messages", conversationPublicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode getBackofficeDisputes(User user) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/backoffice/disputes").with(auth(user)))
            .andExpect(status().isOk())
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

    private StoredDispute loadLatestDispute(long orderId) {
        return jdbcTemplate.queryForObject(
            """
                select public_id::text,
                       status,
                       assigned_support_user_id,
                       taken_at,
                       resolved_at,
                       resolved_by_user_id,
                       resolution_type,
                       resolution_note
                from order_disputes
                where order_id = ?
                order by created_at desc, id desc
                limit 1
                """,
            (rs, rowNum) -> new StoredDispute(
                UUID.fromString(rs.getString("public_id")),
                rs.getString("status"),
                rs.getObject("assigned_support_user_id", Long.class),
                rs.getTimestamp("taken_at") == null ? null : rs.getTimestamp("taken_at").toInstant(),
                rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toInstant(),
                rs.getObject("resolved_by_user_id", Long.class),
                rs.getString("resolution_type"),
                rs.getString("resolution_note")
            ),
            orderId
        );
    }

    private int loadSystemMessageCount(long orderId, String body) {
        return jdbcTemplate.queryForObject(
            """
                select count(*)
                from order_messages m
                join order_conversations c
                  on c.id = m.conversation_id
                where c.order_id = ?
                  and m.message_type = 'system'
                  and m.body = ?
                """,
            Integer.class,
            orderId,
            body
        );
    }

    private int loadConversationParticipantCount(long orderId, String conversationType) {
        return jdbcTemplate.queryForObject(
            """
                select count(*)
                from order_conversation_participants p
                join order_conversations c
                  on c.id = p.conversation_id
                where c.order_id = ?
                  and c.conversation_type = ?
                """,
            Integer.class,
            orderId,
            conversationType
        );
    }

    private String loadConversationPublicId(long orderId, String conversationType) {
        return jdbcTemplate.queryForObject(
            """
                select public_id::text
                from order_conversations
                where order_id = ?
                  and conversation_type = ?
                """,
            String.class,
            orderId,
            conversationType
        );
    }

    private String loadOrderStatus(String orderPublicId) {
        return jdbcTemplate.queryForObject(
            "select status from orders where public_id = ?",
            String.class,
            UUID.fromString(orderPublicId)
        );
    }

    private BigDecimal loadOrderedQuantity(String orderPublicId) {
        return jdbcTemplate.queryForObject(
            "select ordered_quantity from orders where public_id = ?",
            BigDecimal.class,
            UUID.fromString(orderPublicId)
        );
    }

    private String loadHoldStatus(long orderId) {
        return jdbcTemplate.queryForObject(
            """
                select status
                from user_account_holds
                where ref_type = 'order'
                  and ref_id = ?
                  and reason = 'order_funds_hold'
                """,
            String.class,
            orderId
        );
    }

    private BigDecimal loadHoldAmount(long orderId) {
        return jdbcTemplate.queryForObject(
            """
                select amount
                from user_account_holds
                where ref_type = 'order'
                  and ref_id = ?
                  and reason = 'order_funds_hold'
                """,
            BigDecimal.class,
            orderId
        );
    }

    private String loadReservationStatus(long orderId) {
        return jdbcTemplate.queryForObject(
            "select status from offer_reservations where order_id = ?",
            String.class,
            orderId
        );
    }

    private BigDecimal loadReservedAmount(Long userId, String currencyCode) {
        return jdbcTemplate.queryForObject(
            """
                select reserved
                from user_accounts
                where user_id = ?
                  and currency_code = ?
                """,
            BigDecimal.class,
            userId,
            currencyCode
        );
    }

    private long countUserTxs(String refType, long refId) {
        return jdbcTemplate.queryForObject(
            """
                select count(*)
                from user_account_txs
                where ref_type = ?
                  and ref_id = ?
                """,
            Long.class,
            refType,
            refId
        );
    }

    private long countPlatformTxs(String refType, long refId) {
        return jdbcTemplate.queryForObject(
            """
                select count(*)
                from platform_account_txs
                where ref_type = ?
                  and ref_id = ?
                """,
            Long.class,
            refType,
            refId
        );
    }

    private BigDecimal loadUserTxAmount(Long userId, String currencyCode, String refType, long refId) {
        return jdbcTemplate.queryForObject(
            """
                select tx.amount
                from user_account_txs tx
                join user_accounts ua
                  on ua.id = tx.user_account_id
                where ua.user_id = ?
                  and ua.currency_code = ?
                  and tx.ref_type = ?
                  and tx.ref_id = ?
                """,
            BigDecimal.class,
            userId,
            currencyCode,
            refType,
            refId
        );
    }

    private BigDecimal loadPlatformTxAmount(String currencyCode, String refType, long refId) {
        return jdbcTemplate.queryForObject(
            """
                select tx.amount
                from platform_account_txs tx
                join platform_accounts pa
                  on pa.id = tx.platform_account_id
                where pa.currency_code = ?
                  and tx.ref_type = ?
                  and tx.ref_id = ?
                """,
            BigDecimal.class,
            currencyCode,
            refType,
            refId
        );
    }

    private List<String> loadOrderEventTypes(long orderId) {
        return jdbcTemplate.query(
            """
                select event_type
                from order_events
                where order_id = ?
                order by created_at asc, id asc
                """,
            (rs, rowNum) -> rs.getString("event_type"),
            orderId
        );
    }

    private JsonNode findConversationByType(JsonNode conversations, String conversationType) {
        for (JsonNode item : conversations.path("items")) {
            if (conversationType.equals(item.path("conversationType").asText())) {
                return item;
            }
        }
        throw new AssertionError("Conversation type not found: " + conversationType);
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

    private String createOrderRequest(String quoteId, String quantity) {
        return """
            {
              "quoteId": "%s",
              "quantity": %s
            }
            """.formatted(quoteId, quantity);
    }

    private String activeOfferRequest(
        Long gameId,
        Long categoryId,
        String side,
        String currencyCode,
        String priceAmount,
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
              "maxTradeQuantity": 100,
              "quantityStep": 5,
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
            """.formatted(gameId, categoryId, side, title, currencyCode, priceAmount);
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private record StoredDispute(
        UUID publicId,
        String status,
        Long assignedSupportUserId,
        Instant takenAt,
        Instant resolvedAt,
        Long resolvedByUserId,
        String resolutionType,
        String resolutionNote
    ) {
    }
}
