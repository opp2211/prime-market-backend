package ru.maltsev.primemarketbackend.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.sql.Timestamp;
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
import ru.maltsev.primemarketbackend.notification.domain.Notification;
import ru.maltsev.primemarketbackend.notification.domain.NotificationTypes;
import ru.maltsev.primemarketbackend.notification.repository.NotificationRepository;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.support.AbstractPostgresIntegrationTest;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class NotificationApiIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final String BUY_QUOTE_DISPLAY_PRICE = "238.09523810";

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

    @Autowired
    private NotificationRepository notificationRepository;

    private final AtomicLong testRefIdSequence = new AtomicLong(1000L);

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("""
            truncate table
                notifications,
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
                withdrawal_requests,
                payout_profiles,
                deposit_requests,
                deposit_methods,
                user_account_txs,
                user_accounts,
                offer_delivery_methods,
                offer_attribute_values,
                offer_context_values,
                offers
            restart identity cascade
            """);
        jdbcTemplate.update("delete from users where email like ?", "%.notification.example.test");
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
    void listNotificationsReturnsOnlyCurrentUserNotificationsNewestFirst() throws Exception {
        User user = createUser("notifications-owner");
        User otherUser = createUser("notifications-other");
        Notification older = saveNotification(
            user,
            NotificationTypes.ORDER_CREATED,
            Instant.parse("2026-04-20T10:00:00Z"),
            false
        );
        Notification newer = saveNotification(
            user,
            NotificationTypes.ORDER_STATUS_CHANGED,
            Instant.parse("2026-04-20T11:00:00Z"),
            true
        );
        saveNotification(
            otherUser,
            NotificationTypes.ORDER_MESSAGE_RECEIVED,
            Instant.parse("2026-04-20T12:00:00Z"),
            false
        );

        JsonNode response = listNotifications(user, null);

        assertThat(response.path("content")).hasSize(2);
        assertThat(response.path("content").get(0).path("publicId").asText()).isEqualTo(newer.getPublicId().toString());
        assertThat(response.path("content").get(1).path("publicId").asText()).isEqualTo(older.getPublicId().toString());
        assertThat(response.path("totalElements").asLong()).isEqualTo(2L);
    }

    @Test
    void listNotificationsReturnsPlainPayloadObject() throws Exception {
        User user = createUser("notifications-list-payload");
        String orderPublicId = UUID.randomUUID().toString();
        String conversationPublicId = UUID.randomUUID().toString();
        saveNotification(
            user,
            NotificationTypes.ORDER_MESSAGE_RECEIVED,
            Instant.parse("2026-04-20T13:00:00Z"),
            false,
            notificationPayload(orderPublicId, conversationPublicId)
        );

        JsonNode response = listNotifications(user, null);
        JsonNode payload = response.path("content").get(0).path("payload");

        assertPlainNotificationPayload(payload, orderPublicId, conversationPublicId);
    }

    @Test
    void unreadCountReflectsMarkReadChanges() throws Exception {
        User user = createUser("notifications-count-owner");
        User otherUser = createUser("notifications-count-other");
        Notification firstUnread = saveNotification(
            user,
            NotificationTypes.ORDER_CREATED,
            Instant.parse("2026-04-20T10:00:00Z"),
            false
        );
        saveNotification(
            user,
            NotificationTypes.ORDER_MESSAGE_RECEIVED,
            Instant.parse("2026-04-20T10:05:00Z"),
            false
        );
        saveNotification(
            user,
            NotificationTypes.ORDER_STATUS_CHANGED,
            Instant.parse("2026-04-20T10:10:00Z"),
            true
        );
        saveNotification(
            otherUser,
            NotificationTypes.ORDER_CREATED,
            Instant.parse("2026-04-20T10:15:00Z"),
            false
        );

        assertThat(unreadCount(user)).isEqualTo(2L);

        markNotificationRead(user, firstUnread.getPublicId().toString());

        assertThat(unreadCount(user)).isEqualTo(1L);
    }

    @Test
    void markOneAsReadSetsReadStateAndIsIdempotent() throws Exception {
        User user = createUser("notifications-mark-one");
        Notification notification = saveNotification(
            user,
            NotificationTypes.ORDER_REQUEST_CREATED,
            Instant.parse("2026-04-20T12:00:00Z"),
            false
        );

        JsonNode firstResponse = markNotificationRead(user, notification.getPublicId().toString());
        Instant persistedReadAt = loadNotificationReadAt(notification.getPublicId());

        assertThat(firstResponse.path("isRead").asBoolean()).isTrue();
        assertThat(firstResponse.path("readAt").asText()).isNotBlank();
        assertThat(loadNotificationReadState(notification.getPublicId())).isTrue();
        assertThat(persistedReadAt).isNotNull();

        JsonNode secondResponse = markNotificationRead(user, notification.getPublicId().toString());

        assertThat(secondResponse.path("isRead").asBoolean()).isTrue();
        assertThat(loadNotificationReadAt(notification.getPublicId())).isEqualTo(persistedReadAt);
    }

    @Test
    void markReadReturnsPlainPayloadObject() throws Exception {
        User user = createUser("notifications-mark-payload");
        String orderPublicId = UUID.randomUUID().toString();
        String conversationPublicId = UUID.randomUUID().toString();
        Notification notification = saveNotification(
            user,
            NotificationTypes.ORDER_MESSAGE_RECEIVED,
            Instant.parse("2026-04-20T12:30:00Z"),
            false,
            notificationPayload(orderPublicId, conversationPublicId)
        );

        JsonNode response = markNotificationRead(user, notification.getPublicId().toString());

        assertThat(response.path("isRead").asBoolean()).isTrue();
        assertPlainNotificationPayload(response.path("payload"), orderPublicId, conversationPublicId);
    }

    @Test
    void markAllReadMarksOnlyCurrentUserNotifications() throws Exception {
        User user = createUser("notifications-mark-all-owner");
        User otherUser = createUser("notifications-mark-all-other");
        saveNotification(
            user,
            NotificationTypes.ORDER_CREATED,
            Instant.parse("2026-04-20T09:00:00Z"),
            false
        );
        saveNotification(
            user,
            NotificationTypes.ORDER_MESSAGE_RECEIVED,
            Instant.parse("2026-04-20T09:05:00Z"),
            false
        );
        saveNotification(
            user,
            NotificationTypes.ORDER_STATUS_CHANGED,
            Instant.parse("2026-04-20T09:10:00Z"),
            true
        );
        saveNotification(
            otherUser,
            NotificationTypes.ORDER_CREATED,
            Instant.parse("2026-04-20T09:15:00Z"),
            false
        );

        JsonNode response = markAllNotificationsRead(user);

        assertThat(response.path("updatedCount").asLong()).isEqualTo(2L);
        assertThat(unreadCount(user)).isZero();
        assertThat(unreadCount(otherUser)).isEqualTo(1L);
    }

    @Test
    void orderCreatedFlowCreatesNotificationForMaker() throws Exception {
        User seller = createUser("notification-order-maker");
        User buyer = createUser("notification-order-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20", "Notification order created");

        StoredNotification notification = findNotification(seller.getId(), NotificationTypes.ORDER_CREATED);
        assertThat(notification.payload().path("orderPublicId").asText()).isEqualTo(order.path("publicId").asText());
        assertThat(loadNotificationsByType(buyer.getId(), NotificationTypes.ORDER_CREATED)).isEmpty();
    }

    @Test
    void orderMessageFlowCreatesNotificationForOtherParticipantOnly() throws Exception {
        User seller = createUser("notification-message-seller");
        User buyer = createUser("notification-message-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20", "Notification message order");
        String conversationPublicId = findConversationByType(
            getOrderConversations(buyer, order.path("publicId").asText()),
            "order_main"
        ).path("publicId").asText();

        sendOrderMessage(buyer, conversationPublicId, "Нужна доставка сегодня");

        StoredNotification notification = findNotification(seller.getId(), NotificationTypes.ORDER_MESSAGE_RECEIVED);
        assertThat(notification.payload().path("orderPublicId").asText()).isEqualTo(order.path("publicId").asText());
        assertThat(notification.payload().path("conversationPublicId").asText()).isEqualTo(conversationPublicId);
        assertThat(loadNotificationsByType(buyer.getId(), NotificationTypes.ORDER_MESSAGE_RECEIVED)).isEmpty();
    }

    @Test
    void depositConfirmedFlowCreatesNotificationForOwner() throws Exception {
        User user = createUser("notification-deposit-owner");
        User support = loadUserWithRoles("sup1@123.123");
        long depositMethodId = insertDepositMethod("Bank transfer", "RUB");

        JsonNode created = createDepositRequest(user, """
            {
              "deposit_method_id": %d,
              "amount": 1500.0000
            }
            """.formatted(depositMethodId));

        markDepositPaid(user, created.path("public_id").asText());
        confirmDeposit(support, created.path("public_id").asText());

        StoredNotification notification = findNotification(user.getId(), NotificationTypes.DEPOSIT_CONFIRMED);
        assertThat(notification.payload().path("depositRequestPublicId").asText())
            .isEqualTo(created.path("public_id").asText());
    }

    @Test
    void withdrawalRejectedFlowCreatesNotificationForOwner() throws Exception {
        User user = createUser("notification-withdrawal-owner");
        User support = loadUserWithRoles("sup1@123.123");
        fundWallet(user, "USD", "1000.0000");
        long withdrawalMethodId = loadWithdrawalMethodId("BINANCE_UID", "USD");

        JsonNode created = createWithdrawal(user, """
            {
              "currency_code": "USD",
              "withdrawal_method_id": %d,
              "amount": 250.0000,
              "requisites": {
                "uid": "77889911"
              }
            }
            """.formatted(withdrawalMethodId));

        takeWithdrawal(support, created.path("public_id").asText());
        rejectWithdrawal(support, created.path("public_id").asText(), """
            {
              "rejection_reason": "Manual review failed",
              "operator_comment": "Bad requisites"
            }
            """);

        StoredNotification notification = findNotification(user.getId(), NotificationTypes.WITHDRAWAL_REJECTED);
        assertThat(notification.payload().path("withdrawalRequestPublicId").asText())
            .isEqualTo(created.path("public_id").asText());
    }

    private User createUser(String slug) {
        String username = slug;
        if (username.length() > 24) {
            String hash = Integer.toUnsignedString(slug.hashCode(), 36);
            int prefixLength = Math.max(1, 24 - hash.length() - 1);
            username = slug.substring(0, prefixLength) + "-" + hash;
        }
        User user = new User(username, username + "@notification.example.test", "password-hash");
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private User loadUserWithRoles(String email) {
        return userRepository.findWithRolesByEmailIgnoreCase(email)
            .orElseThrow(() -> new IllegalStateException("User not found: " + email));
    }

    private RequestPostProcessor auth(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return authentication(new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        ));
    }

    private JsonNode listNotifications(User user, Boolean isRead) throws Exception {
        var request = get("/api/notifications").with(auth(user)).param("size", "20");
        if (isRead != null) {
            request.param("isRead", isRead.toString());
        }
        MvcResult result = mockMvc.perform(request)
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private long unreadCount(User user) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/notifications/unread-count").with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result).path("count").asLong();
    }

    private JsonNode markNotificationRead(User user, String publicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/notifications/{publicId}/read", publicId).with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode markAllNotificationsRead(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/notifications/read-all").with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private Notification saveNotification(User user, String type, Instant createdAt, boolean isRead) {
        return saveNotification(
            user,
            type,
            createdAt,
            isRead,
            notificationPayload(UUID.randomUUID().toString(), null)
        );
    }

    private Notification saveNotification(User user, String type, Instant createdAt, boolean isRead, ObjectNode payload) {
        Notification notification = notificationRepository.saveAndFlush(new Notification(
            UUID.randomUUID(),
            user.getId(),
            type,
            "Test title " + type,
            "Test body " + type,
            payload
        ));
        jdbcTemplate.update(
            "update notifications set created_at = ?, is_read = ?, read_at = ? where id = ?",
            Timestamp.from(createdAt),
            isRead,
            isRead ? Timestamp.from(createdAt.plusSeconds(30)) : null,
            notification.getId()
        );
        return notificationRepository.findById(notification.getId()).orElseThrow();
    }

    private ObjectNode notificationPayload(String orderPublicId, String conversationPublicId) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode()
            .put("orderPublicId", orderPublicId);
        if (conversationPublicId != null) {
            payload.put("conversationPublicId", conversationPublicId);
        }
        return payload;
    }

    private void assertPlainNotificationPayload(JsonNode payload, String orderPublicId, String conversationPublicId) {
        assertThat(payload.isObject()).isTrue();
        assertThat(payload.size()).isEqualTo(conversationPublicId == null ? 1 : 2);
        assertThat(payload.path("orderPublicId").asText()).isEqualTo(orderPublicId);
        if (conversationPublicId != null) {
            assertThat(payload.path("conversationPublicId").asText()).isEqualTo(conversationPublicId);
        }
        assertThat(payload.has("array")).isFalse();
        assertThat(payload.has("containerNode")).isFalse();
        assertThat(payload.has("nodeType")).isFalse();
    }

    private boolean loadNotificationReadState(UUID publicId) {
        Boolean value = jdbcTemplate.queryForObject(
            "select is_read from notifications where public_id = ?",
            Boolean.class,
            publicId
        );
        return Boolean.TRUE.equals(value);
    }

    private Instant loadNotificationReadAt(UUID publicId) {
        Timestamp timestamp = jdbcTemplate.queryForObject(
            "select read_at from notifications where public_id = ?",
            Timestamp.class,
            publicId
        );
        return timestamp == null ? null : timestamp.toInstant();
    }

    private List<StoredNotification> loadNotificationsByType(Long userId, String type) {
        return jdbcTemplate.query(
            """
                select public_id::text as public_id, type, title, body, payload::text as payload, is_read, created_at, read_at
                from notifications
                where user_id = ?
                  and type = ?
                order by created_at desc, id desc
                """,
            (rs, rowNum) -> new StoredNotification(
                UUID.fromString(rs.getString("public_id")),
                rs.getString("type"),
                rs.getString("title"),
                rs.getString("body"),
                readJson(rs.getString("payload")),
                rs.getBoolean("is_read"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant()
            ),
            userId,
            type
        );
    }

    private StoredNotification findNotification(Long userId, String type) {
        return loadNotificationsByType(userId, type).stream()
            .findFirst()
            .orElseThrow(() -> new AssertionError("Notification not found for user " + userId + " and type " + type));
    }

    private Category requireCategory(String gameSlug, String categorySlug) {
        return categoryRepository.findActiveByGameSlugAndCategorySlug(gameSlug, categorySlug)
            .orElseThrow();
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

    private JsonNode createPendingSellOrder(User seller, User buyer, String quantity, String title) throws Exception {
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", title);
        JsonNode quote = createQuote(offerId, "buy", "RUB", 1L, BUY_QUOTE_DISPLAY_PRICE);
        return createOrder(buyer, quote.path("quoteId").asText(), quantity);
    }

    private JsonNode getOrderConversations(User user, String orderPublicId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/orders/{orderId}/conversations", orderPublicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode sendOrderMessage(User user, String conversationPublicId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/order-conversations/{conversationId}/messages", conversationPublicId)
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "body": "%s"
                    }
                    """.formatted(body)))
            .andExpect(status().isCreated())
            .andReturn();
        return readBody(result);
    }

    private JsonNode createDepositRequest(User user, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/deposit-requests")
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return readBody(result);
    }

    private JsonNode markDepositPaid(User user, String publicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/deposit-requests/{publicId}/mark-paid", publicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode confirmDeposit(User user, String publicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/backoffice/deposit-requests/{publicId}/confirm", publicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode createWithdrawal(User user, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/withdrawal-requests")
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return readBody(result);
    }

    private JsonNode takeWithdrawal(User user, String publicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/backoffice/withdrawal-requests/{publicId}/take", publicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode rejectWithdrawal(User user, String publicId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/backoffice/withdrawal-requests/{publicId}/reject", publicId)
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
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

    private long loadWithdrawalMethodId(String code, String currencyCode) {
        return jdbcTemplate.queryForObject(
            "select id from withdrawal_methods where code = ? and currency_code = ?",
            Long.class,
            code,
            currencyCode
        );
    }

    private long insertDepositMethod(String title, String currencyCode) {
        Long id = jdbcTemplate.queryForObject(
            """
                insert into deposit_methods (title, currency_code, payment_details, auto_confirmation, is_active)
                values (?, ?, ?::jsonb, false, true)
                returning id
                """,
            Long.class,
            title,
            currencyCode,
            "{\"account\":\"123456\"}"
        );
        if (id == null) {
            throw new IllegalStateException("Failed to insert deposit method");
        }
        return id;
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

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read JSON value: " + value, ex);
        }
    }

    private record StoredNotification(
        UUID publicId,
        String type,
        String title,
        String body,
        JsonNode payload,
        boolean isRead,
        Instant createdAt,
        Instant readAt
    ) {
    }
}
