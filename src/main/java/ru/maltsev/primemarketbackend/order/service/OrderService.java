package ru.maltsev.primemarketbackend.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.config.OrderProperties;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.offer.domain.Offer;
import ru.maltsev.primemarketbackend.offer.repository.OfferRepository;
import ru.maltsev.primemarketbackend.offer.service.OfferQuantityRules;
import ru.maltsev.primemarketbackend.notification.service.NotificationService;
import ru.maltsev.primemarketbackend.order.api.dto.CreateOrderRequest;
import ru.maltsev.primemarketbackend.order.api.dto.OrderResponse;
import ru.maltsev.primemarketbackend.order.domain.OfferReservation;
import ru.maltsev.primemarketbackend.order.domain.Order;
import ru.maltsev.primemarketbackend.order.repository.OfferReservationRepository;
import ru.maltsev.primemarketbackend.order.repository.OrderRepository;
import ru.maltsev.primemarketbackend.orderquote.domain.OrderQuote;
import ru.maltsev.primemarketbackend.orderquote.repository.OrderQuoteRepository;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class OrderService {
    private static final String ROLE_BUYER = "buyer";
    private static final String ROLE_SELLER = "seller";
    private static final String SIDE_SELL = "sell";
    private static final String STATUS_PENDING = "pending";
    private static final int ORDER_MONEY_SCALE = 8;
    private static final BigDecimal BPS_DIVISOR = new BigDecimal("10000");
    private static final BigDecimal ZERO_ORDER_QUANTITY = BigDecimal.ZERO.setScale(ORDER_MONEY_SCALE, RoundingMode.HALF_UP);

    private final OrderRepository orderRepository;
    private final OfferReservationRepository offerReservationRepository;
    private final OrderQuoteRepository orderQuoteRepository;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final OrderProperties orderProperties;
    private final FundsHoldService fundsHoldService;
    private final OrderEventWriteService orderEventWriteService;
    private final OrderConversationService orderConversationService;
    private final NotificationService notificationService;

    @Transactional
    public OrderResponse createOrder(Long takerUserId, CreateOrderRequest request) {
        OrderQuote quote = orderQuoteRepository.findByPublicIdForUpdate(requireQuoteId(request))
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "ORDER_QUOTE_NOT_FOUND",
                "Order quote not found"
            ));

        validateQuoteCanBeConsumed(quote);
        if (quote.getOwnerUserId().equals(takerUserId)) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "CANNOT_CREATE_ORDER_ON_OWN_OFFER",
                "Cannot create order on own offer"
            );
        }

        BigDecimal requestedQuantity = requireValidQuantity(request.quantity());
        validateRequestedQuantity(requestedQuantity, quote);

        Offer offer = offerRepository.findByIdForUpdate(quote.getOfferId())
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.CONFLICT,
                "OFFER_UNAVAILABLE",
                "Offer is unavailable"
            ));
        ensureOfferAvailable(offer);

        BigDecimal activeReservedQuantity = offerReservationRepository.sumQuantityByOfferIdAndStatus(
            offer.getId(),
            FundsHoldService.STATUS_ACTIVE
        );
        BigDecimal availableQuantity = offer.getQuantity().subtract(activeReservedQuantity);
        if (availableQuantity.compareTo(requestedQuantity) < 0) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_AVAILABLE_QUANTITY",
                "Insufficient available quantity"
            );
        }

        OrderRoles roles = resolveRoles(quote, takerUserId);
        OrderAmounts amounts = calculateOrderAmounts(quote, requestedQuantity);
        Instant expiresAt = Instant.now().plus(orderProperties.pendingTtl());
        User takerUser = userRepository.findById(takerUserId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "User not found"
            ));
        Order order = orderRepository.saveAndFlush(
            buildOrder(quote, takerUser.getUsername(), requestedQuantity, roles, amounts, expiresAt)
        );
        offerReservationRepository.save(new OfferReservation(
            order.getId(),
            offer.getId(),
            requestedQuantity,
            FundsHoldService.STATUS_ACTIVE,
            expiresAt
        ));
        reserveFunds(quote, offer, order, roles, amounts, expiresAt);
        quote.markConsumed();
        orderConversationService.createMainConversation(order);
        orderEventWriteService.recordOrderCreated(order, takerUserId, roles.takerRole());
        notificationService.notifyOrderCreated(order);

        return OrderResponse.from(order);
    }

    private UUID requireQuoteId(CreateOrderRequest request) {
        if (request == null || request.quoteId() == null) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Field 'quoteId' is required"
            );
        }
        return request.quoteId();
    }

    private void validateQuoteCanBeConsumed(OrderQuote quote) {
        if (quote.isConsumed()) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "ORDER_QUOTE_CONSUMED",
                "Order quote is already consumed"
            );
        }
        if (quote.isInvalidated()) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "ORDER_QUOTE_INVALIDATED",
                "Order quote is invalidated"
            );
        }
        if (!quote.isActive() || !quote.getExpiresAt().isAfter(Instant.now())) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "ORDER_QUOTE_EXPIRED",
                "Order quote is expired"
            );
        }
    }

    private BigDecimal requireValidQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_ORDER_QUANTITY",
                "Order quantity must be positive"
            );
        }
        return quantity.stripTrailingZeros();
    }

    private void validateRequestedQuantity(BigDecimal requestedQuantity, OrderQuote quote) {
        if (quote.getMinTradeQuantitySnapshot() != null
            && requestedQuantity.compareTo(quote.getMinTradeQuantitySnapshot()) < 0) {
            throw invalidOrderQuantity();
        }
        if (quote.getMaxTradeQuantitySnapshot() != null
            && requestedQuantity.compareTo(quote.getMaxTradeQuantitySnapshot()) > 0) {
            throw invalidOrderQuantity();
        }
        if (requestedQuantity.compareTo(quote.getQuantitySnapshot()) > 0) {
            throw invalidOrderQuantity();
        }
        if (quote.getQuantityStepSnapshot() != null
            && !OfferQuantityRules.isAlignedToStep(requestedQuantity, quote.getQuantityStepSnapshot())) {
            throw invalidOrderQuantity();
        }
    }

    private ApiProblemException invalidOrderQuantity() {
        return new ApiProblemException(
            HttpStatus.BAD_REQUEST,
            "INVALID_ORDER_QUANTITY",
            "Order quantity does not match quote constraints"
        );
    }

    private void ensureOfferAvailable(Offer offer) {
        if (!FundsHoldService.STATUS_ACTIVE.equals(offer.getStatus())
            || offer.getPublishedAt() == null
            || offer.getQuantity() == null
            || offer.getQuantity().signum() <= 0) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "OFFER_UNAVAILABLE",
                "Offer is unavailable"
            );
        }
    }

    private OrderRoles resolveRoles(OrderQuote quote, Long takerUserId) {
        if (SIDE_SELL.equals(quote.getOfferSideSnapshot())) {
            return new OrderRoles(
                quote.getOwnerUserId(),
                takerUserId,
                ROLE_SELLER,
                ROLE_BUYER,
                takerUserId
            );
        }

        return new OrderRoles(
            quote.getOwnerUserId(),
            takerUserId,
            ROLE_BUYER,
            ROLE_SELLER,
            quote.getOwnerUserId()
        );
    }

    private OrderAmounts calculateOrderAmounts(OrderQuote quote, BigDecimal requestedQuantity) {
        BigDecimal displayTotalAmount = scaleOrderMoney(
            quote.getDisplayUnitPriceAmount().multiply(requestedQuantity)
        );
        BigDecimal sellerGrossAmount = scaleOrderMoney(
            quote.getOfferPriceAmountSnapshot().multiply(requestedQuantity)
        );
        int sellerFeeBps = orderProperties.defaultSellerFeeBps();
        BigDecimal sellerFeeAmount = sellerGrossAmount
            .multiply(BigDecimal.valueOf(sellerFeeBps))
            .divide(BPS_DIVISOR, ORDER_MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal sellerNetAmount = scaleOrderMoney(sellerGrossAmount.subtract(sellerFeeAmount));
        return new OrderAmounts(
            displayTotalAmount,
            sellerGrossAmount,
            sellerFeeBps,
            sellerFeeAmount,
            sellerNetAmount
        );
    }

    private void reserveFunds(
        OrderQuote quote,
        Offer offer,
        Order order,
        OrderRoles roles,
        OrderAmounts amounts,
        Instant expiresAt
    ) {
        if (SIDE_SELL.equals(quote.getOfferSideSnapshot())) {
            fundsHoldService.createOrderFundsHold(
                order.getId(),
                roles.buyerUserId(),
                quote.getViewerCurrencyCode(),
                amounts.displayTotalAmount(),
                expiresAt
            );
            return;
        }

        fundsHoldService.allocateBuyOfferFundsHold(
            offer,
            order.getId(),
            amounts.sellerGrossAmount(),
            expiresAt
        );
    }

    private Order buildOrder(
        OrderQuote quote,
        String takerUsername,
        BigDecimal requestedQuantity,
        OrderRoles roles,
        OrderAmounts amounts,
        Instant expiresAt
    ) {
        return new Order(
            UUID.randomUUID(),
            quote.getId(),
            roles.makerUserId(),
            roles.takerUserId(),
            roles.makerRole(),
            roles.takerRole(),
            quote.getGameIdSnapshot(),
            quote.getGameSlugSnapshot(),
            quote.getGameTitleSnapshot(),
            quote.getCategoryIdSnapshot(),
            quote.getCategorySlugSnapshot(),
            quote.getCategoryTitleSnapshot(),
            quote.getOfferSideSnapshot(),
            quote.getIntent(),
            quote.getOwnerUsernameSnapshot(),
            takerUsername,
            quote.getTitleSnapshot(),
            quote.getDescriptionSnapshot(),
            quote.getTradeTermsSnapshot(),
            requestedQuantity,
            ZERO_ORDER_QUANTITY,
            quote.getOfferPriceCurrencyCodeSnapshot(),
            quote.getOfferPriceAmountSnapshot(),
            quote.getViewerCurrencyCode(),
            quote.getFxFromCurrencyCode(),
            quote.getFxToCurrencyCode(),
            quote.getFxRate(),
            quote.getDisplayUnitPriceAmount(),
            amounts.displayTotalAmount(),
            amounts.sellerGrossAmount(),
            amounts.sellerFeeBps(),
            amounts.sellerFeeAmount(),
            amounts.sellerNetAmount(),
            quote.getContextsSnapshot().deepCopy(),
            quote.getAttributesSnapshot().deepCopy(),
            quote.getDeliveryMethodsSnapshot().deepCopy(),
            STATUS_PENDING,
            expiresAt
        );
    }

    private BigDecimal scaleOrderMoney(BigDecimal amount) {
        return amount.setScale(ORDER_MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private record OrderRoles(
        Long makerUserId,
        Long takerUserId,
        String makerRole,
        String takerRole,
        Long buyerUserId
    ) {
    }

    private record OrderAmounts(
        BigDecimal displayTotalAmount,
        BigDecimal sellerGrossAmount,
        int sellerFeeBps,
        BigDecimal sellerFeeAmount,
        BigDecimal sellerNetAmount
    ) {
    }
}
