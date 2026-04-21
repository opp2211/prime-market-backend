package ru.maltsev.primemarketbackend.order.api;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;
import ru.maltsev.primemarketbackend.account.repository.UserAccountRepository;
import ru.maltsev.primemarketbackend.account.service.UserAccountService;
import ru.maltsev.primemarketbackend.category.domain.Category;
import ru.maltsev.primemarketbackend.category.repository.CategoryRepository;
import ru.maltsev.primemarketbackend.order.domain.OfferReservation;
import ru.maltsev.primemarketbackend.order.domain.UserAccountHold;
import ru.maltsev.primemarketbackend.order.domain.UserAccountHoldAllocation;
import ru.maltsev.primemarketbackend.order.repository.OfferReservationRepository;
import ru.maltsev.primemarketbackend.order.repository.UserAccountHoldAllocationRepository;
import ru.maltsev.primemarketbackend.order.repository.UserAccountHoldRepository;
import ru.maltsev.primemarketbackend.order.service.OrderLifecycleService;
import ru.maltsev.primemarketbackend.orderquote.repository.OrderQuoteRepository;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.support.AbstractPostgresIntegrationTest;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OrderApiIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final String BUY_QUOTE_DISPLAY_PRICE = "238.09523810";
    private static final String SELL_QUOTE_DISPLAY_PRICE = "231.25000000";

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
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private OfferReservationRepository offerReservationRepository;

    @Autowired
    private UserAccountHoldRepository userAccountHoldRepository;

    @Autowired
    private UserAccountHoldAllocationRepository userAccountHoldAllocationRepository;

    @Autowired
    private OrderQuoteRepository orderQuoteRepository;

    @Autowired
    private OrderLifecycleService orderLifecycleService;

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
        jdbcTemplate.update("delete from users where email like ?", "%.order.example.test");
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
    void createOrderFromActiveSellQuoteReservesOfferAndFundsAndConsumesQuote() throws Exception {
        User seller = createUser("order-seller");
        User buyer = createUser("order-buyer");
        fundWallet(buyer, "RUB", "10000.0000");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "Sell Divine Orb");
        JsonNode quote = createQuote(offerId, "buy", "RUB", 1L, BUY_QUOTE_DISPLAY_PRICE);

        JsonNode order = createOrder(buyer, quote.path("quoteId").asText(), "20");
        assertThat(order.path("status").asText()).isEqualTo("pending");
        assertThat(order.path("makerRole").asText()).isEqualTo("seller");
        assertThat(order.path("takerRole").asText()).isEqualTo("buyer");
        assertThat(order.path("orderedQuantity").decimalValue()).isEqualByComparingTo("20");
        assertThat(order.path("deliveredQuantity").decimalValue()).isEqualByComparingTo("0");
        assertThat(order.path("displayUnitPriceAmount").decimalValue()).isEqualByComparingTo("238.09523810");
        assertThat(order.path("displayTotalAmount").decimalValue()).isEqualByComparingTo("4761.90476200");
        assertThat(order.path("viewerCurrencyCode").asText()).isEqualTo("RUB");
        assertThat(order.path("sellerGrossAmount").decimalValue()).isEqualByComparingTo("50.00000000");
        assertThat(order.path("sellerFeeAmount").decimalValue()).isEqualByComparingTo("0.50000000");
        assertThat(order.path("sellerNetAmount").decimalValue()).isEqualByComparingTo("49.50000000");
        assertThat(order.path("expiresAt").asText()).isNotBlank();
        assertThat(order.path("createdAt").asText()).isNotBlank();

        long orderId = order.path("id").asLong();
        OfferReservation reservation = offerReservationRepository.findByOrderId(orderId).orElseThrow();
        assertThat(reservation.getOfferId()).isEqualTo(offerId);
        assertThat(reservation.getQuantity()).isEqualByComparingTo("20");
        assertThat(reservation.getStatus()).isEqualTo("active");

        UserAccountHold hold = userAccountHoldRepository.findByRef("order", orderId, "order_funds_hold").orElseThrow();
        assertThat(hold.getAmount()).isEqualByComparingTo("4761.9048");
        assertThat(hold.getStatus()).isEqualTo("active");
        assertThat(hold.getReason()).isEqualTo("order_funds_hold");

        String quoteStatus = jdbcTemplate.queryForObject(
            "select status from order_quotes where public_id = ?",
            String.class,
            UUID.fromString(quote.path("quoteId").asText())
        );
        assertThat(quoteStatus).isEqualTo("consumed");

        UserAccount buyerWallet = userAccountRepository.findByUserIdAndCurrencyCode(buyer.getId(), "RUB").orElseThrow();
        assertThat(buyerWallet.getReserved()).isEqualByComparingTo("4761.9048");
    }

    @Test
    void orderCreatedEventExistsAfterCreateOrder() throws Exception {
        User seller = createUser("event-created-seller");
        User buyer = createUser("event-created-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");

        List<StoredOrderEvent> events = loadOrderEvents(order.path("id").asLong());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo("order_created");
        assertThat(events.get(0).actorUserId()).isEqualTo(buyer.getId());
        assertThat(events.get(0).actorRole()).isEqualTo("buyer");
        assertThat(events.get(0).payload().path("orderedQuantity").decimalValue()).isEqualByComparingTo("20");
        assertThat(events.get(0).payload().path("displayTotalAmount").decimalValue()).isEqualByComparingTo("4761.90476200");
        assertThat(events.get(0).payload().path("viewerCurrencyCode").asText()).isEqualTo("RUB");
    }

    @Test
    void createOrderFromActiveBuyQuoteAllocatesExistingOfferFundingWithoutDoubleReserve() throws Exception {
        User buyerMaker = createUser("buy-maker");
        User sellerTaker = createUser("sell-taker");
        fundWallet(buyerMaker, "USD", "500.0000");
        long offerId = createActiveOffer(buyerMaker, "buy", "USD", "2.50", "Buy Divine Orb");
        JsonNode quote = createQuote(offerId, "sell", "RUB", 1L, SELL_QUOTE_DISPLAY_PRICE);

        UserAccount buyerWalletBefore = userAccountRepository.findByUserIdAndCurrencyCode(buyerMaker.getId(), "USD").orElseThrow();
        assertThat(buyerWalletBefore.getReserved()).isEqualByComparingTo("250.0000");

        JsonNode order = createOrder(sellerTaker, quote.path("quoteId").asText(), "20");
        long orderId = order.path("id").asLong();
        assertThat(order.path("makerRole").asText()).isEqualTo("buyer");
        assertThat(order.path("takerRole").asText()).isEqualTo("seller");
        assertThat(order.path("sellerGrossAmount").decimalValue()).isEqualByComparingTo("50.00000000");

        OfferReservation reservation = offerReservationRepository.findByOrderId(orderId).orElseThrow();
        assertThat(reservation.getOfferId()).isEqualTo(offerId);
        assertThat(reservation.getQuantity()).isEqualByComparingTo("20");

        assertThat(userAccountHoldRepository.findByRef("order", orderId, "order_funds_hold")).isEmpty();

        UserAccountHold buyOfferHold = userAccountHoldRepository.findByRef("offer", offerId, "buy_offer_funds_hold")
            .orElseThrow();
        UserAccountHoldAllocation allocation = userAccountHoldAllocationRepository.findByOrderId(orderId).orElseThrow();
        UserAccount buyerWalletAfter = userAccountRepository.findByUserIdAndCurrencyCode(buyerMaker.getId(), "USD").orElseThrow();
        assertThat(buyOfferHold.getAmount()).isEqualByComparingTo("250.0000");
        assertThat(allocation.getUserAccountHoldId()).isEqualTo(buyOfferHold.getId());
        assertThat(allocation.getAmount()).isEqualByComparingTo("50.0000");
        assertThat(allocation.getStatus()).isEqualTo("active");
        assertThat(buyerWalletAfter.getReserved()).isEqualByComparingTo("250.0000");
        assertThat(userAccountRepository.findByUserIdAndCurrencyCode(sellerTaker.getId(), "RUB")).isEmpty();
    }

    @Test
    void makerCanConfirmPendingOrderReady() throws Exception {
        User seller = createUser("confirm-seller");
        User buyer = createUser("confirm-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        JsonNode confirmed = confirmReady(seller, order.path("publicId").asText());

        assertThat(confirmed.path("status").asText()).isEqualTo("in_progress");
        assertThat(confirmed.path("updatedAt").asText()).isNotBlank();
        assertThat(loadOrderStatus(order.path("publicId").asText())).isEqualTo("in_progress");

        UserAccountHold hold = userAccountHoldRepository.findByRef("order", order.path("id").asLong(), "order_funds_hold").orElseThrow();
        OfferReservation reservation = offerReservationRepository.findByOrderId(order.path("id").asLong()).orElseThrow();
        assertThat(hold.getStatus()).isEqualTo("active");
        assertThat(reservation.getStatus()).isEqualTo("active");
    }

    @Test
    void makerConfirmedReadyEventExistsAfterConfirmReady() throws Exception {
        User seller = createUser("event-confirm-seller");
        User buyer = createUser("event-confirm-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        confirmReady(seller, order.path("publicId").asText());

        List<StoredOrderEvent> events = loadOrderEvents(order.path("id").asLong());
        assertThat(events).hasSize(2);
        assertThat(events.get(1).eventType()).isEqualTo("maker_confirmed_ready");
        assertThat(events.get(1).actorUserId()).isEqualTo(seller.getId());
        assertThat(events.get(1).actorRole()).isEqualTo("seller");
        assertThat(events.get(1).payload().path("confirmedByRole").asText()).isEqualTo("seller");
    }

    @Test
    void takerCannotConfirmReady() throws Exception {
        User seller = createUser("confirm-denied-seller");
        User buyer = createUser("confirm-denied-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");

        mockMvc.perform(post("/api/orders/{orderId}/confirm-ready", order.path("publicId").asText())
                .with(auth(buyer)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ONLY_MAKER_CAN_CONFIRM_READY"));
    }

    @Test
    void cannotConfirmNonPendingOrder() throws Exception {
        User seller = createUser("confirm-repeat-seller");
        User buyer = createUser("confirm-repeat-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        confirmReady(seller, order.path("publicId").asText());

        mockMvc.perform(post("/api/orders/{orderId}/confirm-ready", order.path("publicId").asText())
                .with(auth(seller)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_ALREADY_IN_PROGRESS"));
    }

    @Test
    void cancelPendingSellOfferOrderReleasesHoldAndReservation() throws Exception {
        User seller = createUser("cancel-sell-seller");
        User buyer = createUser("cancel-sell-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        JsonNode canceled = cancelOrder(buyer, order.path("publicId").asText());

        long orderId = order.path("id").asLong();
        UserAccountHold hold = userAccountHoldRepository.findByRef("order", orderId, "order_funds_hold").orElseThrow();
        OfferReservation reservation = offerReservationRepository.findByOrderId(orderId).orElseThrow();
        UserAccount buyerWallet = userAccountRepository.findByUserIdAndCurrencyCode(buyer.getId(), "RUB").orElseThrow();

        assertThat(canceled.path("status").asText()).isEqualTo("canceled");
        assertThat(hold.getStatus()).isEqualTo("released");
        assertThat(reservation.getStatus()).isEqualTo("released");
        assertThat(buyerWallet.getReserved()).isEqualByComparingTo("0.0000");
    }

    @Test
    void orderCanceledEventExistsAfterCancel() throws Exception {
        User seller = createUser("event-cancel-seller");
        User buyer = createUser("event-cancel-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        cancelOrder(buyer, order.path("publicId").asText());

        List<StoredOrderEvent> events = loadOrderEvents(order.path("id").asLong());
        assertThat(events).hasSize(2);
        assertThat(events.get(1).eventType()).isEqualTo("order_canceled");
        assertThat(events.get(1).actorUserId()).isEqualTo(buyer.getId());
        assertThat(events.get(1).actorRole()).isEqualTo("buyer");
        assertThat(events.get(1).payload().path("canceledByRole").asText()).isEqualTo("buyer");
    }

    @Test
    void cancelPendingBuyOfferOrderReleasesAllocationOnly() throws Exception {
        User buyerMaker = createUser("cancel-buy-maker");
        User sellerTaker = createUser("cancel-buy-seller");
        fundWallet(buyerMaker, "USD", "500.0000");

        JsonNode order = createPendingBuyOrder(buyerMaker, sellerTaker, "20");
        long offerId = loadOfferIdForOrder(order.path("id").asLong());
        JsonNode canceled = cancelOrder(sellerTaker, order.path("publicId").asText());

        UserAccountHold parentHold = userAccountHoldRepository.findByRef("offer", offerId, "buy_offer_funds_hold").orElseThrow();
        UserAccountHoldAllocation allocation = userAccountHoldAllocationRepository.findByOrderId(order.path("id").asLong()).orElseThrow();
        OfferReservation reservation = offerReservationRepository.findByOrderId(order.path("id").asLong()).orElseThrow();
        UserAccount buyerWallet = userAccountRepository.findByUserIdAndCurrencyCode(buyerMaker.getId(), "USD").orElseThrow();

        assertThat(canceled.path("status").asText()).isEqualTo("canceled");
        assertThat(allocation.getStatus()).isEqualTo("released");
        assertThat(parentHold.getStatus()).isEqualTo("active");
        assertThat(parentHold.getAmount()).isEqualByComparingTo("250.0000");
        assertThat(reservation.getStatus()).isEqualTo("released");
        assertThat(buyerWallet.getReserved()).isEqualByComparingTo("250.0000");
    }

    @Test
    void sellerCanMarkPartiallyDelivered() throws Exception {
        User seller = createUser("partial-seller");
        User buyer = createUser("partial-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        confirmReady(seller, order.path("publicId").asText());

        JsonNode partiallyDelivered = markPartiallyDelivered(seller, order.path("publicId").asText(), "15");

        assertThat(partiallyDelivered.path("status").asText()).isEqualTo("partially_delivered");
        assertThat(partiallyDelivered.path("deliveredQuantity").decimalValue()).isEqualByComparingTo("15");
        assertThat(loadOrderStatus(order.path("publicId").asText())).isEqualTo("partially_delivered");

        List<StoredOrderEvent> events = loadOrderEvents(order.path("id").asLong());
        assertThat(events).hasSize(3);
        assertThat(events.get(2).eventType()).isEqualTo("seller_marked_partial_delivery");
        assertThat(events.get(2).actorUserId()).isEqualTo(seller.getId());
        assertThat(events.get(2).actorRole()).isEqualTo("seller");
        assertThat(events.get(2).payload().path("deliveredQuantity").decimalValue()).isEqualByComparingTo("15");
    }

    @Test
    void buyerCannotMarkPartiallyDelivered() throws Exception {
        User seller = createUser("partial-denied-seller");
        User buyer = createUser("partial-denied-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        confirmReady(seller, order.path("publicId").asText());

        mockMvc.perform(post("/api/orders/{orderId}/mark-partially-delivered", order.path("publicId").asText())
                .with(auth(buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "deliveredQuantity": 15
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ONLY_SELLER_CAN_MARK_PARTIALLY_DELIVERED"));
    }

    @Test
    void sellerCanMarkDelivered() throws Exception {
        User seller = createUser("delivered-seller");
        User buyer = createUser("delivered-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        confirmReady(seller, order.path("publicId").asText());

        JsonNode delivered = markDelivered(seller, order.path("publicId").asText());

        assertThat(delivered.path("status").asText()).isEqualTo("delivered");
        assertThat(delivered.path("deliveredQuantity").decimalValue()).isEqualByComparingTo("20");

        List<StoredOrderEvent> events = loadOrderEvents(order.path("id").asLong());
        assertThat(events).hasSize(3);
        assertThat(events.get(2).eventType()).isEqualTo("seller_marked_delivered");
        assertThat(events.get(2).payload().path("deliveredQuantity").decimalValue()).isEqualByComparingTo("20");
    }

    @Test
    void buyerCanConfirmReceivedAndCompleteSellOfferOrder() throws Exception {
        User seller = createUser("complete-sell-seller");
        User buyer = createUser("complete-sell-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        long orderId = order.path("id").asLong();
        long offerId = loadOfferIdForOrder(orderId);
        confirmReady(seller, order.path("publicId").asText());
        markDelivered(seller, order.path("publicId").asText());

        JsonNode completed = confirmReceived(buyer, order.path("publicId").asText());

        UserAccountHold hold = userAccountHoldRepository.findByRef("order", orderId, "order_funds_hold").orElseThrow();
        OfferReservation reservation = offerReservationRepository.findByOrderId(orderId).orElseThrow();
        UserAccount buyerWallet = userAccountRepository.findByUserIdAndCurrencyCode(buyer.getId(), "RUB").orElseThrow();
        UserAccount sellerWallet = userAccountRepository.findByUserIdAndCurrencyCode(seller.getId(), "USD").orElseThrow();

        assertThat(completed.path("status").asText()).isEqualTo("completed");
        assertThat(completed.path("deliveredQuantity").decimalValue()).isEqualByComparingTo("20");
        assertThat(loadOrderStatus(order.path("publicId").asText())).isEqualTo("completed");
        assertThat(hold.getStatus()).isEqualTo("consumed");
        assertThat(reservation.getStatus()).isEqualTo("consumed");
        assertThat(loadOfferQuantity(offerId)).isEqualByComparingTo("80.00000000");
        assertThat(loadOfferMaxTradeQuantity(offerId)).isEqualByComparingTo("100.00000000");
        assertThat(buyerWallet.getReserved()).isEqualByComparingTo("0.0000");
        assertThat(buyerWallet.getBalance()).isEqualByComparingTo("5238.0952");
        assertThat(sellerWallet.getBalance()).isEqualByComparingTo("49.5000");
        assertThat(loadPlatformBalance("USD")).isEqualByComparingTo("0.5000");
        assertThat(loadUserTxAmount(buyer.getId(), "RUB", "ORDER_BUYER_SETTLEMENT", orderId))
            .isEqualByComparingTo("-4761.9048");
        assertThat(loadUserTxAmount(seller.getId(), "USD", "ORDER_SELLER_PAYOUT", orderId))
            .isEqualByComparingTo("49.5000");
        assertThat(loadPlatformTxAmount("USD", "ORDER_PLATFORM_FEE", orderId))
            .isEqualByComparingTo("0.5000");

        List<StoredOrderEvent> events = loadOrderEvents(orderId);
        assertThat(events).hasSize(5);
        assertThat(events.get(3).eventType()).isEqualTo("buyer_confirmed_received");
        assertThat(events.get(4).eventType()).isEqualTo("order_completed");
    }

    @Test
    void buyerCanConfirmReceivedAndCompleteBuyOfferOrder() throws Exception {
        User buyerMaker = createUser("complete-buy-maker");
        User sellerTaker = createUser("complete-buy-seller");
        fundWallet(buyerMaker, "USD", "500.0000");

        JsonNode order = createPendingBuyOrder(buyerMaker, sellerTaker, "20");
        long orderId = order.path("id").asLong();
        long offerId = loadOfferIdForOrder(orderId);
        confirmReady(buyerMaker, order.path("publicId").asText());

        JsonNode completed = confirmReceived(buyerMaker, order.path("publicId").asText());

        UserAccountHold parentHold = userAccountHoldRepository.findByRef("offer", offerId, "buy_offer_funds_hold")
            .orElseThrow();
        UserAccountHoldAllocation allocation = userAccountHoldAllocationRepository.findByOrderId(orderId).orElseThrow();
        OfferReservation reservation = offerReservationRepository.findByOrderId(orderId).orElseThrow();
        UserAccount buyerWallet = userAccountRepository.findByUserIdAndCurrencyCode(buyerMaker.getId(), "USD").orElseThrow();
        UserAccount sellerWallet = userAccountRepository.findByUserIdAndCurrencyCode(sellerTaker.getId(), "USD").orElseThrow();

        assertThat(completed.path("status").asText()).isEqualTo("completed");
        assertThat(completed.path("deliveredQuantity").decimalValue()).isEqualByComparingTo("20");
        assertThat(allocation.getStatus()).isEqualTo("consumed");
        assertThat(parentHold.getStatus()).isEqualTo("active");
        assertThat(parentHold.getAmount()).isEqualByComparingTo("200.0000");
        assertThat(reservation.getStatus()).isEqualTo("consumed");
        assertThat(loadOfferQuantity(offerId)).isEqualByComparingTo("80.00000000");
        assertThat(loadOfferMaxTradeQuantity(offerId)).isEqualByComparingTo("100.00000000");
        assertThat(buyerWallet.getReserved()).isEqualByComparingTo("200.0000");
        assertThat(buyerWallet.getBalance()).isEqualByComparingTo("450.0000");
        assertThat(sellerWallet.getBalance()).isEqualByComparingTo("49.5000");
        assertThat(loadPlatformBalance("USD")).isEqualByComparingTo("0.5000");
        assertThat(loadUserTxAmount(buyerMaker.getId(), "USD", "ORDER_BUY_OFFER_CONSUMPTION", orderId))
            .isEqualByComparingTo("-50.0000");
        assertThat(loadUserTxAmount(sellerTaker.getId(), "USD", "ORDER_SELLER_PAYOUT", orderId))
            .isEqualByComparingTo("49.5000");
        assertThat(loadPlatformTxAmount("USD", "ORDER_PLATFORM_FEE", orderId))
            .isEqualByComparingTo("0.5000");

        List<StoredOrderEvent> events = loadOrderEvents(orderId);
        assertThat(events).hasSize(4);
        assertThat(events.get(2).eventType()).isEqualTo("buyer_confirmed_received");
        assertThat(events.get(3).eventType()).isEqualTo("order_completed");
    }

    @Test
    void invalidDeliveryAndCompletionTransitionsAreRejected() throws Exception {
        User seller = createUser("transition-seller");
        User buyer = createUser("transition-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        confirmReady(seller, order.path("publicId").asText());

        mockMvc.perform(post("/api/orders/{orderId}/confirm-received", order.path("publicId").asText())
                .with(auth(seller)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ONLY_BUYER_CAN_CONFIRM_RECEIVED"));

        mockMvc.perform(post("/api/orders/{orderId}/mark-delivered", order.path("publicId").asText())
                .with(auth(buyer)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ONLY_SELLER_CAN_MARK_DELIVERED"));

        markDelivered(seller, order.path("publicId").asText());
        confirmReceived(buyer, order.path("publicId").asText());

        mockMvc.perform(post("/api/orders/{orderId}/confirm-received", order.path("publicId").asText())
                .with(auth(buyer)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_ALREADY_COMPLETED"));
    }

    @Test
    void cannotCreateFromExpiredQuote() throws Exception {
        User seller = createUser("expired-seller");
        User buyer = createUser("expired-buyer");
        fundWallet(buyer, "RUB", "10000.0000");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "Expired quote offer");
        JsonNode quote = createQuote(offerId, "buy", "RUB", 1L, BUY_QUOTE_DISPLAY_PRICE);

        expireQuote(UUID.fromString(quote.path("quoteId").asText()));

        mockMvc.perform(post("/api/orders")
                .with(auth(buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createOrderRequest(quote.path("quoteId").asText(), "20")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_QUOTE_EXPIRED"));
    }

    @Test
    void cannotCreateFromConsumedQuote() throws Exception {
        User seller = createUser("consumed-seller");
        User buyer = createUser("consumed-buyer");
        User anotherBuyer = createUser("consumed-another-buyer");
        fundWallet(buyer, "RUB", "10000.0000");
        fundWallet(anotherBuyer, "RUB", "10000.0000");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "Consumed quote offer");
        JsonNode quote = createQuote(offerId, "buy", "RUB", 1L, BUY_QUOTE_DISPLAY_PRICE);

        createOrder(buyer, quote.path("quoteId").asText(), "20");

        mockMvc.perform(post("/api/orders")
                .with(auth(anotherBuyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createOrderRequest(quote.path("quoteId").asText(), "20")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_QUOTE_CONSUMED"));
    }

    @Test
    void cannotCreateOnOwnOffer() throws Exception {
        User seller = createUser("own-offer-seller");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "Own offer");
        JsonNode quote = createQuote(offerId, "buy", "RUB", 1L, BUY_QUOTE_DISPLAY_PRICE);

        mockMvc.perform(post("/api/orders")
                .with(auth(seller))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createOrderRequest(quote.path("quoteId").asText(), "20")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CANNOT_CREATE_ORDER_ON_OWN_OFFER"));
    }

    @Test
    void cannotCreateOrderWhenFundsAreInsufficient() throws Exception {
        User seller = createUser("funds-seller");
        User buyer = createUser("funds-buyer");
        fundWallet(buyer, "RUB", "1000.0000");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "Funds offer");
        JsonNode quote = createQuote(offerId, "buy", "RUB", 1L, BUY_QUOTE_DISPLAY_PRICE);

        mockMvc.perform(post("/api/orders")
                .with(auth(buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createOrderRequest(quote.path("quoteId").asText(), "20")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void cannotCreateOrderWhenAvailableQuantityIsInsufficient() throws Exception {
        User seller = createUser("quantity-seller");
        User firstBuyer = createUser("quantity-buyer-first");
        User secondBuyer = createUser("quantity-buyer-second");
        fundWallet(firstBuyer, "RUB", "50000.0000");
        fundWallet(secondBuyer, "RUB", "50000.0000");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "Quantity offer");
        JsonNode firstQuote = createQuote(offerId, "buy", "RUB", 1L, BUY_QUOTE_DISPLAY_PRICE);
        JsonNode secondQuote = createQuote(offerId, "buy", "RUB", 1L, BUY_QUOTE_DISPLAY_PRICE);
        createOrder(firstBuyer, firstQuote.path("quoteId").asText(), "90");

        mockMvc.perform(post("/api/orders")
                .with(auth(secondBuyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createOrderRequest(secondQuote.path("quoteId").asText(), "20")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_AVAILABLE_QUANTITY"));
    }

    @Test
    void activeBuyOfferReservationsReduceAvailableCapacity() throws Exception {
        User buyerMaker = createUser("capacity-maker");
        User firstSeller = createUser("capacity-seller-1");
        User secondSeller = createUser("capacity-seller-2");
        fundWallet(buyerMaker, "USD", "500.0000");
        long offerId = createActiveOffer(buyerMaker, "buy", "USD", "2.50", "Capacity buy offer");
        JsonNode firstQuote = createQuote(offerId, "sell", "RUB", 1L, SELL_QUOTE_DISPLAY_PRICE);
        JsonNode secondQuote = createQuote(offerId, "sell", "RUB", 1L, SELL_QUOTE_DISPLAY_PRICE);

        createOrder(firstSeller, firstQuote.path("quoteId").asText(), "90");

        mockMvc.perform(post("/api/orders")
                .with(auth(secondSeller))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createOrderRequest(secondQuote.path("quoteId").asText(), "20")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_AVAILABLE_QUANTITY"));
    }

    @Test
    void onlyParticipantCanCancelOrder() throws Exception {
        User seller = createUser("cancel-participant-seller");
        User buyer = createUser("cancel-participant-buyer");
        User stranger = createUser("cancel-participant-stranger");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");

        mockMvc.perform(post("/api/orders/{orderId}/cancel", order.path("publicId").asText())
                .with(auth(stranger)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ONLY_ORDER_PARTICIPANT_CAN_CANCEL"));
    }

    @Test
    void buyerCannotDirectCancelInProgressOrder() throws Exception {
        User seller = createUser("cancel-in-progress-seller");
        User buyer = createUser("cancel-in-progress-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        confirmReady(seller, order.path("publicId").asText());

        mockMvc.perform(post("/api/orders/{orderId}/cancel", order.path("publicId").asText())
                .with(auth(buyer)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ONLY_SELLER_CAN_DIRECT_CANCEL"));
    }

    @Test
    void sellerCanDirectCancelInProgressOrderAndReleaseResources() throws Exception {
        User seller = createUser("direct-cancel-progress-s");
        User buyer = createUser("direct-cancel-progress-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        confirmReady(seller, order.path("publicId").asText());

        JsonNode canceled = cancelOrder(seller, order.path("publicId").asText());

        UserAccountHold hold = userAccountHoldRepository.findByRef("order", order.path("id").asLong(), "order_funds_hold")
            .orElseThrow();
        OfferReservation reservation = offerReservationRepository.findByOrderId(order.path("id").asLong()).orElseThrow();
        UserAccount buyerWallet = userAccountRepository.findByUserIdAndCurrencyCode(buyer.getId(), "RUB").orElseThrow();
        assertThat(canceled.path("status").asText()).isEqualTo("canceled");
        assertThat(hold.getStatus()).isEqualTo("released");
        assertThat(reservation.getStatus()).isEqualTo("released");
        assertThat(buyerWallet.getReserved()).isEqualByComparingTo("0.0000");
    }

    @Test
    void sellerCanDirectCancelPartiallyDeliveredAndDeliveredOrders() throws Exception {
        User seller = createUser("direct-cancel-active-s");
        User buyer = createUser("direct-cancel-active-b");
        fundWallet(buyer, "RUB", "30000.0000");

        JsonNode partialOrder = createPendingSellOrder(seller, buyer, "20", "Partial direct cancel");
        confirmReady(seller, partialOrder.path("publicId").asText());
        markPartiallyDelivered(seller, partialOrder.path("publicId").asText(), "10");

        JsonNode deliveredOrder = createPendingSellOrder(seller, buyer, "20", "Delivered direct cancel");
        confirmReady(seller, deliveredOrder.path("publicId").asText());
        markDelivered(seller, deliveredOrder.path("publicId").asText());

        assertThat(cancelOrder(seller, partialOrder.path("publicId").asText()).path("status").asText())
            .isEqualTo("canceled");
        assertThat(cancelOrder(seller, deliveredOrder.path("publicId").asText()).path("status").asText())
            .isEqualTo("canceled");

        assertThat(offerReservationRepository.findByOrderId(partialOrder.path("id").asLong()).orElseThrow().getStatus())
            .isEqualTo("released");
        assertThat(offerReservationRepository.findByOrderId(deliveredOrder.path("id").asLong()).orElseThrow().getStatus())
            .isEqualTo("released");
    }

    @Test
    void buyerCanRequestCancelInProgressAndRequestAppearsPending() throws Exception {
        User seller = createUser("request-cancel-s");
        User buyer = createUser("request-cancel-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        confirmReady(seller, order.path("publicId").asText());

        JsonNode request = requestCancel(buyer, order.path("publicId").asText());
        JsonNode details = getOrderDetails(seller, order.path("publicId").asText());

        assertThat(request.path("requestType").asText()).isEqualTo("cancel");
        assertThat(request.path("status").asText()).isEqualTo("pending");
        assertThat(request.path("requestedByRole").asText()).isEqualTo("buyer");
        assertThat(details.path("pendingRequests")).hasSize(1);
        assertThat(details.path("pendingRequests").get(0).path("requestType").asText()).isEqualTo("cancel");
        assertThat(details.path("pendingRequests").get(0).path("canApprove").asBoolean()).isTrue();
    }

    @Test
    void sellerCannotCreateRequestCancelWhenDirectCancelIsAvailable() throws Exception {
        User seller = createUser("request-cancel-deny-s");
        User buyer = createUser("request-cancel-deny-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        confirmReady(seller, order.path("publicId").asText());

        mockMvc.perform(post("/api/orders/{orderId}/request-cancel", order.path("publicId").asText())
                .with(auth(seller)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ONLY_BUYER_CAN_REQUEST_CANCEL"));
    }

    @Test
    void approveCancelRequestCancelsOrderAndReleasesResources() throws Exception {
        User seller = createUser("approve-cancel-s");
        User buyer = createUser("approve-cancel-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        confirmReady(seller, order.path("publicId").asText());
        JsonNode request = requestCancel(buyer, order.path("publicId").asText());

        JsonNode approved = approveRequest(seller, request.path("publicId").asText());

        UserAccountHold hold = userAccountHoldRepository.findByRef("order", order.path("id").asLong(), "order_funds_hold")
            .orElseThrow();
        OfferReservation reservation = offerReservationRepository.findByOrderId(order.path("id").asLong()).orElseThrow();
        assertThat(approved.path("status").asText()).isEqualTo("approved");
        assertThat(loadOrderStatus(order.path("publicId").asText())).isEqualTo("canceled");
        assertThat(hold.getStatus()).isEqualTo("released");
        assertThat(reservation.getStatus()).isEqualTo("released");
    }

    @Test
    void amendQuantityRequestValidatesCapacityAndDeliveredQuantity() throws Exception {
        User seller = createUser("amend-create-s");
        User firstBuyer = createUser("amend-create-b1");
        User secondBuyer = createUser("amend-create-b2");
        fundWallet(firstBuyer, "RUB", "30000.0000");
        fundWallet(secondBuyer, "RUB", "30000.0000");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "Amend capacity");

        JsonNode firstQuote = createQuote(offerId, "buy", "RUB", 1L, BUY_QUOTE_DISPLAY_PRICE);
        JsonNode firstOrder = createOrder(firstBuyer, firstQuote.path("quoteId").asText(), "40");
        JsonNode secondQuote = createQuote(offerId, "buy", "RUB", 1L, BUY_QUOTE_DISPLAY_PRICE);
        JsonNode secondOrder = createOrder(secondBuyer, secondQuote.path("quoteId").asText(), "60");
        confirmReady(seller, secondOrder.path("publicId").asText());

        mockMvc.perform(post("/api/orders/{orderId}/request-amend-quantity", secondOrder.path("publicId").asText())
                .with(auth(secondBuyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "quantity": 100
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("AMEND_QUANTITY_EXCEEDS_AVAILABLE_CAPACITY"));

        confirmReady(seller, firstOrder.path("publicId").asText());
        markPartiallyDelivered(seller, firstOrder.path("publicId").asText(), "20");
        mockMvc.perform(post("/api/orders/{orderId}/request-amend-quantity", firstOrder.path("publicId").asText())
                .with(auth(firstBuyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "quantity": 15
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("AMEND_QUANTITY_BELOW_DELIVERED"));

        JsonNode request = requestAmendQuantity(firstBuyer, firstOrder.path("publicId").asText(), "35.5");
        assertThat(request.path("requestType").asText()).isEqualTo("amend_quantity");
        assertThat(request.path("requestedQuantity").decimalValue()).isEqualByComparingTo("35.5");
        assertThat(request.path("status").asText()).isEqualTo("pending");
    }

    @Test
    void approveAmendQuantityForSellOfferRebalancesHoldReservationAndTotals() throws Exception {
        User seller = createUser("amend-sell-s");
        User buyer = createUser("amend-sell-b");
        fundWallet(buyer, "RUB", "30000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        confirmReady(seller, order.path("publicId").asText());
        JsonNode request = requestAmendQuantity(buyer, order.path("publicId").asText(), "30");

        JsonNode approved = approveRequest(seller, request.path("publicId").asText());
        JsonNode details = getOrderDetails(buyer, order.path("publicId").asText());

        UserAccountHold hold = userAccountHoldRepository.findByRef("order", order.path("id").asLong(), "order_funds_hold")
            .orElseThrow();
        OfferReservation reservation = offerReservationRepository.findByOrderId(order.path("id").asLong()).orElseThrow();
        UserAccount buyerWallet = userAccountRepository.findByUserIdAndCurrencyCode(buyer.getId(), "RUB").orElseThrow();
        assertThat(approved.path("status").asText()).isEqualTo("approved");
        assertThat(details.path("orderedQuantity").decimalValue()).isEqualByComparingTo("30");
        assertThat(details.path("price").path("totalAmount").decimalValue()).isEqualByComparingTo("7142.85714300");
        assertThat(details.path("sellerGrossAmount").decimalValue()).isEqualByComparingTo("75.00000000");
        assertThat(details.path("sellerFeeAmount").decimalValue()).isEqualByComparingTo("0.75000000");
        assertThat(details.path("sellerNetAmount").decimalValue()).isEqualByComparingTo("74.25000000");
        assertThat(hold.getAmount()).isEqualByComparingTo("7142.8572");
        assertThat(buyerWallet.getReserved()).isEqualByComparingTo("7142.8572");
        assertThat(reservation.getQuantity()).isEqualByComparingTo("30");
    }

    @Test
    void approveAmendQuantityForBuyOfferRebalancesAllocationWithoutDoubleReserve() throws Exception {
        User buyerMaker = createUser("amend-buy-maker");
        User sellerTaker = createUser("amend-buy-seller");
        fundWallet(buyerMaker, "USD", "500.0000");

        JsonNode order = createPendingBuyOrder(buyerMaker, sellerTaker, "20");
        long offerId = loadOfferIdForOrder(order.path("id").asLong());
        confirmReady(buyerMaker, order.path("publicId").asText());
        JsonNode request = requestAmendQuantity(sellerTaker, order.path("publicId").asText(), "30");

        approveRequest(buyerMaker, request.path("publicId").asText());

        UserAccountHold parentHold = userAccountHoldRepository.findByRef("offer", offerId, "buy_offer_funds_hold")
            .orElseThrow();
        UserAccountHoldAllocation allocation = userAccountHoldAllocationRepository.findByOrderId(order.path("id").asLong())
            .orElseThrow();
        OfferReservation reservation = offerReservationRepository.findByOrderId(order.path("id").asLong()).orElseThrow();
        UserAccount buyerWallet = userAccountRepository.findByUserIdAndCurrencyCode(buyerMaker.getId(), "USD").orElseThrow();
        JsonNode details = getOrderDetails(sellerTaker, order.path("publicId").asText());

        assertThat(parentHold.getStatus()).isEqualTo("active");
        assertThat(parentHold.getAmount()).isEqualByComparingTo("250.0000");
        assertThat(allocation.getAmount()).isEqualByComparingTo("75.0000");
        assertThat(buyerWallet.getReserved()).isEqualByComparingTo("250.0000");
        assertThat(reservation.getQuantity()).isEqualByComparingTo("30");
        assertThat(details.path("orderedQuantity").decimalValue()).isEqualByComparingTo("30");
        assertThat(details.path("sellerGrossAmount").decimalValue()).isEqualByComparingTo("75.00000000");
        assertThat(details.path("sellerFeeAmount").decimalValue()).isEqualByComparingTo("0.75000000");
        assertThat(details.path("sellerNetAmount").decimalValue()).isEqualByComparingTo("74.25000000");
    }

    @Test
    void rejectAmendQuantityRequestLeavesOrderUnchanged() throws Exception {
        User seller = createUser("amend-reject-s");
        User buyer = createUser("amend-reject-b");
        fundWallet(buyer, "RUB", "30000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        confirmReady(seller, order.path("publicId").asText());
        JsonNode request = requestAmendQuantity(buyer, order.path("publicId").asText(), "30");

        JsonNode rejected = rejectRequest(seller, request.path("publicId").asText());
        JsonNode details = getOrderDetails(buyer, order.path("publicId").asText());

        assertThat(rejected.path("status").asText()).isEqualTo("rejected");
        assertThat(details.path("orderedQuantity").decimalValue()).isEqualByComparingTo("20");
        assertThat(details.path("price").path("totalAmount").decimalValue()).isEqualByComparingTo("4761.90476200");
    }

    @Test
    void orderCreationStillEnforcesQuantityStepForRelaxedOfferQuantityRules() throws Exception {
        User seller = createUser("step-relaxed-s");
        User buyer = createUser("step-relaxed-b");
        fundWallet(buyer, "RUB", "30000.0000");
        Category category = requireCategory("path-of-exile", "currency");

        MvcResult offerResult = mockMvc.perform(post("/api/offers")
                .with(auth(seller))
                .contentType(MediaType.APPLICATION_JSON)
                .content(activeOfferRequest(
                    category.getGame().getId(),
                    category.getId(),
                    "sell",
                    "USD",
                    "2.50",
                    "Relaxed quantity"
                ).replace("\"quantity\": 100", "\"quantity\": 101")
                    .replace("\"minTradeQuantity\": 10", "\"minTradeQuantity\": 12")
                    .replace("\"maxTradeQuantity\": 100", "\"maxTradeQuantity\": 250")
                    .replace("\"quantityStep\": 5", "\"quantityStep\": 5")))
            .andExpect(status().isCreated())
            .andReturn();
        long offerId = readBody(offerResult).path("id").asLong();
        JsonNode quote = createQuote(offerId, "buy", "RUB", 1L, BUY_QUOTE_DISPLAY_PRICE);
        assertThat(quote.path("quantity").decimalValue()).isEqualByComparingTo("101");
        assertThat(quote.path("minTradeQuantity").decimalValue()).isEqualByComparingTo("15");
        assertThat(quote.path("maxTradeQuantity").decimalValue()).isEqualByComparingTo("100");

        mockMvc.perform(post("/api/orders")
                .with(auth(buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createOrderRequest(quote.path("quoteId").asText(), "12")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ORDER_QUANTITY"));
    }

    @Test
    void decreasingActiveOfferBelowActiveReservationsIsRejected() throws Exception {
        User seller = createUser("offer-reserved-s");
        User buyer = createUser("offer-reserved-b");
        fundWallet(buyer, "RUB", "30000.0000");
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", "Reserved offer");
        JsonNode quote = createQuote(offerId, "buy", "RUB", 1L, BUY_QUOTE_DISPLAY_PRICE);
        createOrder(buyer, quote.path("quoteId").asText(), "60");

        mockMvc.perform(patch("/api/offers/{offerId}", offerId)
                .with(auth(seller))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "quantity": 50
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OFFER_QUANTITY_BELOW_ACTIVE_RESERVATIONS"));
    }

    @Test
    void expirePendingSellOfferOrderReleasesHoldAndReservation() throws Exception {
        User seller = createUser("expire-sell-seller");
        User buyer = createUser("expire-sell-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        forceOrderExpiration(order.path("publicId").asText());
        int expiredCount = orderLifecycleService.expirePendingOrders(Instant.now());

        long orderId = order.path("id").asLong();
        UserAccountHold hold = userAccountHoldRepository.findByRef("order", orderId, "order_funds_hold").orElseThrow();
        OfferReservation reservation = offerReservationRepository.findByOrderId(orderId).orElseThrow();
        UserAccount buyerWallet = userAccountRepository.findByUserIdAndCurrencyCode(buyer.getId(), "RUB").orElseThrow();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(loadOrderStatus(order.path("publicId").asText())).isEqualTo("expired");
        assertThat(hold.getStatus()).isEqualTo("expired");
        assertThat(reservation.getStatus()).isEqualTo("expired");
        assertThat(buyerWallet.getReserved()).isEqualByComparingTo("0.0000");
    }

    @Test
    void orderExpiredEventExistsAfterExpireFlow() throws Exception {
        User seller = createUser("event-expire-seller");
        User buyer = createUser("event-expire-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        forceOrderExpiration(order.path("publicId").asText());

        int expiredCount = orderLifecycleService.expirePendingOrders(Instant.now());

        List<StoredOrderEvent> events = loadOrderEvents(order.path("id").asLong());
        assertThat(expiredCount).isEqualTo(1);
        assertThat(events).hasSize(2);
        assertThat(events.get(1).eventType()).isEqualTo("order_expired");
        assertThat(events.get(1).actorUserId()).isNull();
        assertThat(events.get(1).actorRole()).isEqualTo("system");
        assertThat(events.get(1).payload().path("reason").asText()).isEqualTo("pending_timeout");
    }

    @Test
    void expirePendingBuyOfferOrderReleasesAllocationButKeepsParentHold() throws Exception {
        User buyerMaker = createUser("expire-buy-maker");
        User sellerTaker = createUser("expire-buy-seller");
        fundWallet(buyerMaker, "USD", "500.0000");

        JsonNode order = createPendingBuyOrder(buyerMaker, sellerTaker, "20");
        long offerId = loadOfferIdForOrder(order.path("id").asLong());
        forceOrderExpiration(order.path("publicId").asText());
        int expiredCount = orderLifecycleService.expirePendingOrders(Instant.now());

        UserAccountHold parentHold = userAccountHoldRepository.findByRef("offer", offerId, "buy_offer_funds_hold").orElseThrow();
        UserAccountHoldAllocation allocation = userAccountHoldAllocationRepository.findByOrderId(order.path("id").asLong()).orElseThrow();
        OfferReservation reservation = offerReservationRepository.findByOrderId(order.path("id").asLong()).orElseThrow();
        UserAccount buyerWallet = userAccountRepository.findByUserIdAndCurrencyCode(buyerMaker.getId(), "USD").orElseThrow();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(loadOrderStatus(order.path("publicId").asText())).isEqualTo("expired");
        assertThat(allocation.getStatus()).isEqualTo("expired");
        assertThat(parentHold.getStatus()).isEqualTo("active");
        assertThat(parentHold.getAmount()).isEqualByComparingTo("250.0000");
        assertThat(reservation.getStatus()).isEqualTo("expired");
        assertThat(buyerWallet.getReserved()).isEqualByComparingTo("250.0000");
    }

    @Test
    void repeatedCancelOnCanceledOrderFailsPredictably() throws Exception {
        User seller = createUser("repeat-cancel-seller");
        User buyer = createUser("repeat-cancel-buyer");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        cancelOrder(buyer, order.path("publicId").asText());

        mockMvc.perform(post("/api/orders/{orderId}/cancel", order.path("publicId").asText())
                .with(auth(seller)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_ALREADY_CANCELED"));

        mockMvc.perform(post("/api/orders/{orderId}/confirm-ready", order.path("publicId").asText())
                .with(auth(seller)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_ALREADY_CANCELED"));
    }

    @Test
    void getMyOrdersReturnsOrdersWhereUserIsMakerAndTakerAndExcludesOthers() throws Exception {
        User sharedUser = createUser("orders-shared");
        User buyerCounterparty = createUser("orders-buyer");
        User sellerCounterparty = createUser("orders-seller");
        User outsiderSeller = createUser("orders-out-s");
        User outsiderBuyer = createUser("orders-out-b");

        fundWallet(sharedUser, "RUB", "10000.0000");
        fundWallet(buyerCounterparty, "RUB", "10000.0000");
        fundWallet(outsiderBuyer, "RUB", "10000.0000");

        createPendingSellOrder(sharedUser, buyerCounterparty, "20", "Maker order");
        createPendingSellOrder(sellerCounterparty, sharedUser, "25", "Taker order");
        createPendingSellOrder(outsiderSeller, outsiderBuyer, "15", "Outsider order");

        JsonNode response = getMyOrders(sharedUser, null, null, null, null);

        assertThat(response.path("page").asInt()).isEqualTo(0);
        assertThat(response.path("size").asInt()).isEqualTo(20);
        assertThat(response.path("total").asInt()).isEqualTo(2);
        assertThat(response.path("items")).hasSize(2);
        assertThat(response.path("items").get(0).path("title").asText()).isEqualTo("Taker order");
        assertThat(response.path("items").get(1).path("title").asText()).isEqualTo("Maker order");

        JsonNode takerOrder = response.path("items").get(0);
        assertThat(takerOrder.path("myRole").asText()).isEqualTo("buyer");
        assertThat(takerOrder.path("counterpartyRole").asText()).isEqualTo("seller");
        assertThat(takerOrder.path("counterparty").path("username").asText()).isEqualTo(sellerCounterparty.getUsername());
        assertThat(takerOrder.path("game").path("slug").asText()).isEqualTo("path-of-exile");
        assertThat(takerOrder.path("category").path("slug").asText()).isEqualTo("currency");

        JsonNode makerOrder = response.path("items").get(1);
        assertThat(makerOrder.path("myRole").asText()).isEqualTo("seller");
        assertThat(makerOrder.path("counterpartyRole").asText()).isEqualTo("buyer");
        assertThat(makerOrder.path("counterparty").path("username").asText()).isEqualTo(buyerCounterparty.getUsername());
    }

    @Test
    void getMyOrdersReturnsViewerAwareFinancialSummaryForMakerAndTaker() throws Exception {
        User sharedUser = createUser("orders-financial-shared");
        User buyerCounterparty = createUser("orders-financial-buyer");
        User sellerCounterparty = createUser("orders-financial-seller");

        fundWallet(sharedUser, "RUB", "10000.0000");
        fundWallet(buyerCounterparty, "RUB", "10000.0000");

        createPendingSellOrder(sharedUser, buyerCounterparty, "20", "Maker financial row");
        createPendingSellOrder(sellerCounterparty, sharedUser, "25", "Taker financial row");

        JsonNode response = getMyOrders(sharedUser, null, null, null, null);

        JsonNode makerSummary = findOrderByTitle(response, "Maker financial row").path("financialSummary");
        assertThat(makerSummary.path("primaryLabel").asText()).isEqualTo("\u041a \u043f\u043e\u043b\u0443\u0447\u0435\u043d\u0438\u044e");
        assertThat(makerSummary.path("primaryAmount").decimalValue()).isEqualByComparingTo("49.50000000");
        assertThat(makerSummary.path("primaryCurrencyCode").asText()).isEqualTo("USD");
        assertThat(makerSummary.path("viewerPerspective").asText()).isEqualTo("maker");

        JsonNode takerSummary = findOrderByTitle(response, "Taker financial row").path("financialSummary");
        assertThat(takerSummary.path("primaryLabel").asText()).isEqualTo("\u0421\u0443\u043c\u043c\u0430 \u0441\u0434\u0435\u043b\u043a\u0438");
        assertThat(takerSummary.path("primaryAmount").decimalValue()).isEqualByComparingTo("5952.38095250");
        assertThat(takerSummary.path("primaryCurrencyCode").asText()).isEqualTo("RUB");
        assertThat(takerSummary.path("viewerPerspective").asText()).isEqualTo("taker");
    }

    @Test
    void getMyOrdersSupportsStatusAndRoleFilters() throws Exception {
        User sharedUser = createUser("orders-filtered");
        User firstBuyer = createUser("orders-filter-b1");
        User secondBuyer = createUser("orders-filter-b2");
        User sellerCounterparty = createUser("orders-filter-s");

        fundWallet(sharedUser, "RUB", "10000.0000");
        fundWallet(firstBuyer, "RUB", "10000.0000");
        fundWallet(secondBuyer, "RUB", "10000.0000");

        JsonNode inProgressMakerOrder = createPendingSellOrder(sharedUser, firstBuyer, "20", "Maker in progress");
        confirmReady(sharedUser, inProgressMakerOrder.path("publicId").asText());
        createPendingSellOrder(sharedUser, secondBuyer, "25", "Maker pending");
        createPendingSellOrder(sellerCounterparty, sharedUser, "30", "Taker pending");

        JsonNode response = getMyOrders(sharedUser, "in_progress", "maker", 0, 20);

        assertThat(response.path("total").asInt()).isEqualTo(1);
        assertThat(response.path("items")).hasSize(1);
        assertThat(response.path("items").get(0).path("title").asText()).isEqualTo("Maker in progress");
        assertThat(response.path("items").get(0).path("status").asText()).isEqualTo("in_progress");
    }

    @Test
    void getMyOrdersRejectsInvalidPagingParameters() throws Exception {
        User user = createUser("orders-paging");

        mockMvc.perform(get("/api/my/orders")
                .with(auth(user))
                .param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAGE"));

        mockMvc.perform(get("/api/my/orders")
                .with(auth(user))
                .param("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
    }

    @Test
    void takerOrderDetailsExposeDisplayFinancialSummary() throws Exception {
        User seller = createUser("financial-taker-s");
        User buyer = createUser("financial-taker-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20", "Taker financial details");
        JsonNode details = getOrderDetails(buyer, order.path("publicId").asText());

        JsonNode summary = details.path("financialSummary");
        assertThat(summary.path("primaryLabel").asText()).isEqualTo("\u0421\u0443\u043c\u043c\u0430 \u0441\u0434\u0435\u043b\u043a\u0438");
        assertThat(summary.path("primaryAmount").decimalValue()).isEqualByComparingTo("4761.90476200");
        assertThat(summary.path("primaryCurrencyCode").asText()).isEqualTo("RUB");
        assertThat(summary.path("dealAmount").decimalValue()).isEqualByComparingTo("4761.90476200");
        assertThat(summary.path("unitPriceAmount").decimalValue()).isEqualByComparingTo("238.09523810");
        assertThat(summary.path("currencyCode").asText()).isEqualTo("RUB");
        assertThat(summary.path("feeRateBps").isNull()).isTrue();
        assertThat(summary.path("feeRatePercent").isNull()).isTrue();
        assertThat(summary.path("feeAmount").isNull()).isTrue();
        assertThat(summary.path("viewerPerspective").asText()).isEqualTo("taker");
    }

    @Test
    void makerSellerOrderDetailsExposeSettlementFinancialSummaryWithFeeRate() throws Exception {
        User seller = createUser("financial-maker-s");
        User buyer = createUser("financial-maker-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20", "Maker seller financial details");
        JsonNode details = getOrderDetails(seller, order.path("publicId").asText());

        JsonNode summary = details.path("financialSummary");
        assertThat(summary.path("primaryLabel").asText()).isEqualTo("\u041a \u043f\u043e\u043b\u0443\u0447\u0435\u043d\u0438\u044e");
        assertThat(summary.path("primaryAmount").decimalValue()).isEqualByComparingTo("49.50000000");
        assertThat(summary.path("primaryCurrencyCode").asText()).isEqualTo("USD");
        assertThat(summary.path("dealAmount").decimalValue()).isEqualByComparingTo("50.00000000");
        assertThat(summary.path("unitPriceAmount").decimalValue()).isEqualByComparingTo("2.50000000");
        assertThat(summary.path("currencyCode").asText()).isEqualTo("USD");
        assertThat(summary.path("feeRateBps").asInt()).isEqualTo(100);
        assertThat(summary.path("feeRatePercent").decimalValue()).isEqualByComparingTo("1.00");
        assertThat(summary.path("feeAmount").decimalValue()).isEqualByComparingTo("0.50000000");
        assertThat(summary.path("viewerPerspective").asText()).isEqualTo("maker");
        assertThat(details.path("sellerNetAmount").decimalValue()).isEqualByComparingTo("49.50000000");
    }

    @Test
    void makerBuyerOrderDetailsExposeSettlementFinancialSummary() throws Exception {
        User buyerMaker = createUser("financial-maker-buyer");
        User sellerTaker = createUser("financial-seller-taker");
        fundWallet(buyerMaker, "USD", "500.0000");

        JsonNode order = createPendingBuyOrder(buyerMaker, sellerTaker, "20", "Maker buyer financial details");
        JsonNode details = getOrderDetails(buyerMaker, order.path("publicId").asText());

        JsonNode summary = details.path("financialSummary");
        assertThat(summary.path("primaryLabel").asText()).isEqualTo("\u041a \u0441\u043f\u0438\u0441\u0430\u043d\u0438\u044e");
        assertThat(summary.path("primaryAmount").decimalValue()).isEqualByComparingTo("50.00000000");
        assertThat(summary.path("primaryCurrencyCode").asText()).isEqualTo("USD");
        assertThat(summary.path("dealAmount").decimalValue()).isEqualByComparingTo("50.00000000");
        assertThat(summary.path("unitPriceAmount").decimalValue()).isEqualByComparingTo("2.50000000");
        assertThat(summary.path("currencyCode").asText()).isEqualTo("USD");
        assertThat(summary.path("feeRateBps").isNull()).isTrue();
        assertThat(summary.path("feeRatePercent").isNull()).isTrue();
        assertThat(summary.path("feeAmount").isNull()).isTrue();
        assertThat(summary.path("viewerPerspective").asText()).isEqualTo("maker");
    }

    @Test
    void makerPendingOrderDetailsExposeAvailableActions() throws Exception {
        User seller = createUser("details-maker-s");
        User buyer = createUser("details-maker-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20", "Maker details");
        JsonNode details = getOrderDetails(seller, order.path("publicId").asText());

        assertThat(details.path("myRole").asText()).isEqualTo("seller");
        assertThat(details.path("counterpartyRole").asText()).isEqualTo("buyer");
        assertThat(details.path("counterparty").path("username").asText()).isEqualTo(buyer.getUsername());
        assertThat(details.path("availableActions").path("canConfirmReady").asBoolean()).isTrue();
        assertThat(details.path("availableActions").path("canCancel").asBoolean()).isTrue();
        assertThat(details.path("availableActions").path("canMarkPartiallyDelivered").asBoolean()).isFalse();
        assertThat(details.path("availableActions").path("canMarkDelivered").asBoolean()).isFalse();
        assertThat(details.path("availableActions").path("canConfirmReceived").asBoolean()).isFalse();
    }

    @Test
    void takerPendingOrderDetailsExposeFriendlyReadModelAndAvailableActions() throws Exception {
        User seller = createUser("details-taker-s");
        User buyer = createUser("details-taker-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20", "Taker details");
        JsonNode details = getOrderDetails(buyer, order.path("publicId").asText());

        assertThat(details.path("myRole").asText()).isEqualTo("buyer");
        assertThat(details.path("counterpartyRole").asText()).isEqualTo("seller");
        assertThat(details.path("counterparty").path("username").asText()).isEqualTo(seller.getUsername());
        assertThat(details.path("game").path("slug").asText()).isEqualTo("path-of-exile");
        assertThat(details.path("category").path("slug").asText()).isEqualTo("currency");
        assertThat(details.path("price").path("currencyCode").asText()).isEqualTo("RUB");
        assertThat(details.path("contexts").get(0).path("dimensionSlug").asText()).isEqualTo("platform");
        assertThat(details.path("attributes").get(0).path("attributeSlug").asText()).isEqualTo("currency-type");
        assertThat(details.path("deliveryMethods").get(0).path("slug").asText()).isEqualTo("f2f");
        assertThat(details.path("availableActions").path("canConfirmReady").asBoolean()).isFalse();
        assertThat(details.path("availableActions").path("canCancel").asBoolean()).isTrue();
        assertThat(details.path("availableActions").path("canMarkPartiallyDelivered").asBoolean()).isFalse();
        assertThat(details.path("availableActions").path("canMarkDelivered").asBoolean()).isFalse();
        assertThat(details.path("availableActions").path("canConfirmReceived").asBoolean()).isFalse();
    }

    @Test
    void inProgressOrderDetailsExposeNoPendingActions() throws Exception {
        User seller = createUser("details-progress-s");
        User buyer = createUser("details-progress-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20", "In progress details");
        confirmReady(seller, order.path("publicId").asText());

        JsonNode details = getOrderDetails(seller, order.path("publicId").asText());

        assertThat(details.path("status").asText()).isEqualTo("in_progress");
        assertThat(details.path("availableActions").path("canConfirmReady").asBoolean()).isFalse();
        assertThat(details.path("availableActions").path("canCancel").asBoolean()).isTrue();
        assertThat(details.path("availableActions").path("canRequestCancel").asBoolean()).isFalse();
        assertThat(details.path("availableActions").path("canRequestAmendQuantity").asBoolean()).isTrue();
        assertThat(details.path("availableActions").path("canMarkPartiallyDelivered").asBoolean()).isTrue();
        assertThat(details.path("availableActions").path("canMarkDelivered").asBoolean()).isTrue();
        assertThat(details.path("availableActions").path("canConfirmReceived").asBoolean()).isFalse();

        JsonNode buyerDetails = getOrderDetails(buyer, order.path("publicId").asText());
        assertThat(buyerDetails.path("availableActions").path("canConfirmReady").asBoolean()).isFalse();
        assertThat(buyerDetails.path("availableActions").path("canCancel").asBoolean()).isFalse();
        assertThat(buyerDetails.path("availableActions").path("canRequestCancel").asBoolean()).isTrue();
        assertThat(buyerDetails.path("availableActions").path("canRequestAmendQuantity").asBoolean()).isTrue();
        assertThat(buyerDetails.path("availableActions").path("canMarkPartiallyDelivered").asBoolean()).isFalse();
        assertThat(buyerDetails.path("availableActions").path("canMarkDelivered").asBoolean()).isFalse();
        assertThat(buyerDetails.path("availableActions").path("canConfirmReceived").asBoolean()).isTrue();
    }

    @Test
    void orderReadEndpointsRequireAuthAndHideOrdersFromOutsiders() throws Exception {
        User seller = createUser("details-outside-s");
        User buyer = createUser("details-outside-b");
        User outsider = createUser("details-outside-x");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20", "Protected order");

        mockMvc.perform(get("/api/my/orders"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/orders/{orderId}", order.path("publicId").asText()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/orders/{orderId}", order.path("publicId").asText())
                .with(auth(outsider)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void orderCreationAutoCreatesOrderMainConversation() throws Exception {
        User seller = createUser("chat-main-s");
        User buyer = createUser("chat-main-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        long orderId = order.path("id").asLong();

        assertThat(loadConversationCount(orderId, "order_main")).isEqualTo(1);
        assertThat(loadConversationParticipantCount(orderId, "order_main")).isEqualTo(2);

        String mainConversationId = loadConversationPublicId(orderId, "order_main");
        JsonNode messages = getConversationMessages(buyer, mainConversationId);
        assertThat(messages.path("items")).hasSize(1);
        assertThat(messages.path("items").get(0).path("messageType").asText()).isEqualTo("system");
        assertThat(messages.path("items").get(0).path("body").asText()).isEqualTo("Chat opened for this order");
        assertThat(messages.path("items").get(0).path("sender").isNull()).isTrue();
    }

    @Test
    void buyerSeesMainAndOwnSupportConversation() throws Exception {
        User seller = createUser("chat-buyer-s");
        User buyer = createUser("chat-buyer-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        JsonNode conversations = getOrderConversations(buyer, order.path("publicId").asText());

        assertThat(conversations.path("items")).hasSize(2);
        assertThat(findConversationByType(conversations, "order_main").path("publicId").asText()).isNotBlank();
        assertThat(findConversationByType(conversations, "order_support_buyer").path("publicId").asText()).isNotBlank();
        assertThat(loadConversationCount(order.path("id").asLong(), "order_support_buyer")).isEqualTo(1);
        assertThat(loadConversationCount(order.path("id").asLong(), "order_support_seller")).isZero();
    }

    @Test
    void sellerSeesMainAndOwnSupportConversation() throws Exception {
        User seller = createUser("chat-seller-s");
        User buyer = createUser("chat-seller-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        JsonNode conversations = getOrderConversations(seller, order.path("publicId").asText());

        assertThat(conversations.path("items")).hasSize(2);
        assertThat(findConversationByType(conversations, "order_main").path("publicId").asText()).isNotBlank();
        assertThat(findConversationByType(conversations, "order_support_seller").path("publicId").asText()).isNotBlank();
        assertThat(loadConversationCount(order.path("id").asLong(), "order_support_buyer")).isZero();
        assertThat(loadConversationCount(order.path("id").asLong(), "order_support_seller")).isEqualTo(1);
    }

    @Test
    void buyerCannotAccessSellerSupportConversation() throws Exception {
        User seller = createUser("chat-deny-bs");
        User buyer = createUser("chat-deny-bb");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        JsonNode sellerConversations = getOrderConversations(seller, order.path("publicId").asText());
        String sellerSupportId = findConversationByType(
            sellerConversations,
            "order_support_seller"
        ).path("publicId").asText();

        mockMvc.perform(get("/api/order-conversations/{conversationId}/messages", sellerSupportId)
                .with(auth(buyer)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ORDER_CONVERSATION_NOT_FOUND"));
    }

    @Test
    void sellerCannotAccessBuyerSupportConversation() throws Exception {
        User seller = createUser("chat-deny-ss");
        User buyer = createUser("chat-deny-sb");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        JsonNode buyerConversations = getOrderConversations(buyer, order.path("publicId").asText());
        String buyerSupportId = findConversationByType(
            buyerConversations,
            "order_support_buyer"
        ).path("publicId").asText();

        mockMvc.perform(get("/api/order-conversations/{conversationId}/messages", buyerSupportId)
                .with(auth(seller)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ORDER_CONVERSATION_NOT_FOUND"));
    }

    @Test
    void outsiderCannotAccessOrderConversationsOrMessages() throws Exception {
        User seller = createUser("chat-outside-s");
        User buyer = createUser("chat-outside-b");
        User outsider = createUser("chat-outside-x");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        JsonNode buyerConversations = getOrderConversations(buyer, order.path("publicId").asText());
        String mainConversationId = findConversationByType(
            buyerConversations,
            "order_main"
        ).path("publicId").asText();

        mockMvc.perform(get("/api/orders/{orderId}/conversations", order.path("publicId").asText())
                .with(auth(outsider)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        mockMvc.perform(get("/api/order-conversations/{conversationId}/messages", mainConversationId)
                .with(auth(outsider)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ORDER_CONVERSATION_NOT_FOUND"));
    }

    @Test
    void participantCanSendMessageToVisibleConversation() throws Exception {
        User seller = createUser("chat-send-s");
        User buyer = createUser("chat-send-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        JsonNode conversations = getOrderConversations(buyer, order.path("publicId").asText());
        String mainConversationId = findConversationByType(conversations, "order_main")
            .path("publicId")
            .asText();

        JsonNode sent = sendOrderMessage(buyer, mainConversationId, "  Hello seller  ");

        assertThat(sent.path("messageType").asText()).isEqualTo("text");
        assertThat(sent.path("body").asText()).isEqualTo("Hello seller");
        assertThat(sent.path("sender").path("userId").asLong()).isEqualTo(buyer.getId());
        assertThat(sent.path("sender").path("role").asText()).isEqualTo("buyer");
        assertThat(sent.path("sender").path("username").asText()).isEqualTo(buyer.getUsername());

        JsonNode messages = getConversationMessages(seller, mainConversationId);
        assertThat(messages.path("items")).hasSize(2);
        assertThat(messages.path("items").get(1).path("publicId").asText()).isEqualTo(sent.path("publicId").asText());
    }

    @Test
    void systemMessageExistsInNewlyCreatedSupportConversation() throws Exception {
        User seller = createUser("chat-system-s");
        User buyer = createUser("chat-system-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        JsonNode conversations = getOrderConversations(buyer, order.path("publicId").asText());
        String supportConversationId = findConversationByType(
            conversations,
            "order_support_buyer"
        ).path("publicId").asText();

        JsonNode messages = getConversationMessages(buyer, supportConversationId);

        assertThat(messages.path("items")).hasSize(1);
        assertThat(messages.path("items").get(0).path("messageType").asText()).isEqualTo("system");
        assertThat(messages.path("items").get(0).path("body").asText()).isEqualTo("Support chat opened");
        assertThat(messages.path("items").get(0).path("sender").isNull()).isTrue();
    }

    @Test
    void lastMessageAtUpdatesOnSend() throws Exception {
        User seller = createUser("chat-last-s");
        User buyer = createUser("chat-last-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        JsonNode conversations = getOrderConversations(buyer, order.path("publicId").asText());
        String mainConversationId = findConversationByType(conversations, "order_main")
            .path("publicId")
            .asText();
        assertThat(loadConversationLastMessageAt(mainConversationId)).isNull();

        JsonNode sent = sendOrderMessage(buyer, mainConversationId, "Last message timestamp");

        Instant sentAt = Instant.parse(sent.path("createdAt").asText());
        assertThat(loadConversationLastMessageAt(mainConversationId)).isEqualTo(sentAt);

        JsonNode updatedConversations = getOrderConversations(buyer, order.path("publicId").asText());
        Instant listedLastMessageAt = Instant.parse(findConversationByType(updatedConversations, "order_main")
            .path("lastMessageAt")
            .asText());
        assertThat(listedLastMessageAt).isEqualTo(sentAt);
    }

    @Test
    void participantCanReadOrderEvents() throws Exception {
        User seller = createUser("events-reader-s");
        User buyer = createUser("events-reader-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        confirmReady(seller, order.path("publicId").asText());

        JsonNode response = getOrderEvents(buyer, order.path("publicId").asText());

        assertThat(response.path("items")).hasSize(2);
        assertThat(response.path("items").get(0).path("eventType").asText()).isEqualTo("order_created");
        assertThat(response.path("items").get(0).path("actor").path("userId").asLong()).isEqualTo(buyer.getId());
        assertThat(response.path("items").get(0).path("actor").path("role").asText()).isEqualTo("buyer");
        assertThat(response.path("items").get(1).path("eventType").asText()).isEqualTo("maker_confirmed_ready");
        assertThat(response.path("items").get(1).path("actor").path("userId").asLong()).isEqualTo(seller.getId());
        assertThat(response.path("items").get(1).path("actor").path("role").asText()).isEqualTo("seller");
    }

    @Test
    void outsiderCannotReadOrderEvents() throws Exception {
        User seller = createUser("events-outside-s");
        User buyer = createUser("events-outside-b");
        User outsider = createUser("events-outside-x");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");

        mockMvc.perform(get("/api/orders/{orderId}/events", order.path("publicId").asText())
                .with(auth(outsider)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void orderEventsAreReturnedOldestFirst() throws Exception {
        User seller = createUser("events-ordering-s");
        User buyer = createUser("events-ordering-b");
        fundWallet(buyer, "RUB", "10000.0000");

        JsonNode order = createPendingSellOrder(seller, buyer, "20");
        cancelOrder(buyer, order.path("publicId").asText());

        JsonNode response = getOrderEvents(seller, order.path("publicId").asText());

        assertThat(response.path("items")).hasSize(2);
        assertThat(response.path("items").get(0).path("eventType").asText()).isEqualTo("order_created");
        assertThat(response.path("items").get(1).path("eventType").asText()).isEqualTo("order_canceled");
        assertThat(Instant.parse(response.path("items").get(0).path("createdAt").asText()))
            .isBeforeOrEqualTo(Instant.parse(response.path("items").get(1).path("createdAt").asText()));
    }

    @Test
    void getOrCreateWalletLogicReturnsSingleWalletPerCurrency() {
        User user = createUser("wallet-owner");

        UserAccount first = userAccountService.getOrCreateAccount(user.getId(), "RUB");
        UserAccount second = userAccountService.getOrCreateAccount(user.getId(), "RUB");

        Integer walletCount = jdbcTemplate.queryForObject(
            "select count(*) from user_accounts where user_id = ? and currency_code = ?",
            Integer.class,
            user.getId(),
            "RUB"
        );
        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(walletCount).isEqualTo(1);
        assertThat(first.getBalance()).isEqualByComparingTo("0");
        assertThat(first.getReserved()).isEqualByComparingTo("0");
    }

    private User createUser(String slug) {
        String username = slug;
        if (username.length() > 24) {
            String hash = Integer.toUnsignedString(slug.hashCode(), 36);
            int prefixLength = Math.max(1, 24 - hash.length() - 1);
            username = slug.substring(0, prefixLength) + "-" + hash;
        }
        User user = new User(username, username + "@order.example.test", "password-hash");
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

    private JsonNode cancelOrder(User user, String orderPublicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/cancel", orderPublicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode requestCancel(User user, String orderPublicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/request-cancel", orderPublicId)
                .with(auth(user)))
            .andExpect(status().isCreated())
            .andReturn();
        return readBody(result);
    }

    private JsonNode requestAmendQuantity(User user, String orderPublicId, String quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/request-amend-quantity", orderPublicId)
                .with(auth(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "quantity": %s
                    }
                    """.formatted(quantity)))
            .andExpect(status().isCreated())
            .andReturn();
        return readBody(result);
    }

    private JsonNode approveRequest(User user, String requestPublicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/order-requests/{requestId}/approve", requestPublicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode rejectRequest(User user, String requestPublicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/order-requests/{requestId}/reject", requestPublicId)
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

    private JsonNode markDelivered(User user, String orderPublicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/mark-delivered", orderPublicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode confirmReceived(User user, String orderPublicId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders/{orderId}/confirm-received", orderPublicId)
                .with(auth(user)))
            .andExpect(status().isOk())
            .andReturn();
        return readBody(result);
    }

    private JsonNode createPendingSellOrder(User seller, User buyer, String quantity) throws Exception {
        return createPendingSellOrder(seller, buyer, quantity, "Pending sell order");
    }

    private JsonNode createPendingSellOrder(User seller, User buyer, String quantity, String title) throws Exception {
        long offerId = createActiveOffer(seller, "sell", "USD", "2.50", title);
        JsonNode quote = createQuote(offerId, "buy", "RUB", 1L, BUY_QUOTE_DISPLAY_PRICE);
        return createOrder(buyer, quote.path("quoteId").asText(), quantity);
    }

    private JsonNode createPendingBuyOrder(User buyerMaker, User sellerTaker, String quantity) throws Exception {
        return createPendingBuyOrder(buyerMaker, sellerTaker, quantity, "Pending buy order");
    }

    private JsonNode createPendingBuyOrder(User buyerMaker, User sellerTaker, String quantity, String title) throws Exception {
        long offerId = createActiveOffer(buyerMaker, "buy", "USD", "2.50", title);
        JsonNode quote = createQuote(offerId, "sell", "RUB", 1L, SELL_QUOTE_DISPLAY_PRICE);
        return createOrder(sellerTaker, quote.path("quoteId").asText(), quantity);
    }

    private JsonNode getMyOrders(User user, String status, String role, Integer page, Integer size) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/my/orders").with(auth(user));
        if (status != null) {
            request.param("status", status);
        }
        if (role != null) {
            request.param("role", role);
        }
        if (page != null) {
            request.param("page", Integer.toString(page));
        }
        if (size != null) {
            request.param("size", Integer.toString(size));
        }
        MvcResult result = mockMvc.perform(request)
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

    private void expireQuote(UUID quoteId) {
        jdbcTemplate.update(
            "update order_quotes set expires_at = ? where public_id = ?",
            Timestamp.from(Instant.now().minusSeconds(1)),
            quoteId
        );
    }

    private void forceOrderExpiration(String publicOrderId) {
        jdbcTemplate.update(
            "update orders set expires_at = ? where public_id = ?",
            Timestamp.from(Instant.now().minusSeconds(1)),
            UUID.fromString(publicOrderId)
        );
    }

    private String loadOrderStatus(String publicOrderId) {
        return jdbcTemplate.queryForObject(
            "select status from orders where public_id = ?",
            String.class,
            UUID.fromString(publicOrderId)
        );
    }

    private long loadOfferIdForOrder(long orderId) {
        return jdbcTemplate.queryForObject(
            "select offer_id from offer_reservations where order_id = ?",
            Long.class,
            orderId
        );
    }

    private BigDecimal loadOfferQuantity(long offerId) {
        return jdbcTemplate.queryForObject(
            "select quantity from offers where id = ?",
            BigDecimal.class,
            offerId
        );
    }

    private BigDecimal loadOfferMaxTradeQuantity(long offerId) {
        return jdbcTemplate.queryForObject(
            "select max_trade_quantity from offers where id = ?",
            BigDecimal.class,
            offerId
        );
    }

    private BigDecimal loadPlatformBalance(String currencyCode) {
        return jdbcTemplate.queryForObject(
            "select balance from platform_accounts where currency_code = ?",
            BigDecimal.class,
            currencyCode
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

    private int loadConversationCount(long orderId, String conversationType) {
        return jdbcTemplate.queryForObject(
            """
                select count(*)
                from order_conversations
                where order_id = ?
                  and conversation_type = ?
                """,
            Integer.class,
            orderId,
            conversationType
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

    private Instant loadConversationLastMessageAt(String conversationPublicId) {
        Timestamp timestamp = jdbcTemplate.queryForObject(
            """
                select last_message_at
                from order_conversations
                where public_id = ?
                """,
            Timestamp.class,
            UUID.fromString(conversationPublicId)
        );
        return timestamp == null ? null : timestamp.toInstant();
    }

    private JsonNode findConversationByType(JsonNode conversations, String conversationType) {
        for (JsonNode item : conversations.path("items")) {
            if (conversationType.equals(item.path("conversationType").asText())) {
                return item;
            }
        }
        throw new AssertionError("Conversation type not found: " + conversationType);
    }

    private JsonNode findOrderByTitle(JsonNode orders, String title) {
        for (JsonNode item : orders.path("items")) {
            if (title.equals(item.path("title").asText())) {
                return item;
            }
        }
        throw new AssertionError("Order title not found: " + title);
    }

    private List<StoredOrderEvent> loadOrderEvents(long orderId) {
        return jdbcTemplate.query(
            """
                select id, event_type, actor_user_id, actor_role, payload::text as payload, created_at
                from order_events
                where order_id = ?
                order by created_at asc, id asc
                """,
            (rs, rowNum) -> new StoredOrderEvent(
                rs.getLong("id"),
                rs.getString("event_type"),
                rs.getObject("actor_user_id", Long.class),
                rs.getString("actor_role"),
                readJson(rs.getString("payload")),
                rs.getTimestamp("created_at").toInstant()
            ),
            orderId
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

    private record StoredOrderEvent(
        Long id,
        String eventType,
        Long actorUserId,
        String actorRole,
        JsonNode payload,
        Instant createdAt
    ) {
    }
}
