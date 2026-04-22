package ru.maltsev.primemarketbackend.order.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.order.api.dto.MyOrdersRequest;
import ru.maltsev.primemarketbackend.order.api.dto.MyOrdersResponse;
import ru.maltsev.primemarketbackend.order.api.dto.OrderDetailsResponse;
import ru.maltsev.primemarketbackend.order.api.dto.OrderReadModelDtos;
import ru.maltsev.primemarketbackend.order.domain.Order;
import ru.maltsev.primemarketbackend.order.domain.OrderRequest;
import ru.maltsev.primemarketbackend.order.repository.OrderReadQueryRepository;
import ru.maltsev.primemarketbackend.order.repository.OrderRequestRepository;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@Service
@RequiredArgsConstructor
public class OrderReadService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String FILTER_ROLE_MAKER = "maker";
    private static final String FILTER_ROLE_TAKER = "taker";
    private static final String ROLE_BUYER = "buyer";
    private static final String ROLE_SELLER = "seller";
    private static final String VIEWER_PERSPECTIVE_MAKER = "maker";
    private static final String VIEWER_PERSPECTIVE_TAKER = "taker";
    private static final String LABEL_DEAL_AMOUNT = "\u0421\u0443\u043c\u043c\u0430 \u0441\u0434\u0435\u043b\u043a\u0438";
    private static final String LABEL_MAKER_SELLER_PRIMARY = "\u041a \u043f\u043e\u043b\u0443\u0447\u0435\u043d\u0438\u044e";
    private static final String LABEL_MAKER_BUYER_PRIMARY = "\u041a \u0441\u043f\u0438\u0441\u0430\u043d\u0438\u044e";
    private static final int ORDER_MONEY_SCALE = 8;

    private final OrderReadQueryRepository orderReadQueryRepository;
    private final OrderRequestRepository orderRequestRepository;
    private final OrderAccessService orderAccessService;
    private final OrderDisputeService orderDisputeService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public MyOrdersResponse getMyOrders(Long currentUserId, MyOrdersRequest request) {
        int page = normalizePage(request.page());
        int size = normalizeSize(request.size());
        String status = normalizeOptionalValue(request.status());
        String role = normalizeRole(request.role());

        Page<Order> orders = orderReadQueryRepository.findMyOrders(currentUserId, status, role, page, size);
        List<MyOrdersResponse.Item> items = orders.getContent().stream()
            .map(order -> toListItem(order, currentUserId))
            .toList();
        return new MyOrdersResponse(items, page, size, orders.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OrderDetailsResponse getOrderDetails(java.util.UUID publicOrderId, UserPrincipal principal) {
        Order order = orderAccessService.requireReadableOrder(publicOrderId, principal);
        Long currentUserId = principal.getUser().getId();

        boolean viewerIsParticipant = isParticipant(order, currentUserId);
        boolean viewerIsMaker = isMaker(order, currentUserId);
        boolean viewerIsSeller = isSeller(order, currentUserId);
        boolean viewerIsBuyer = isBuyer(order, currentUserId);
        List<OrderReadModelDtos.PendingRequest> pendingRequests = orderRequestRepository
            .findAllByOrderIdAndStatusOrderByCreatedAtDesc(order.getId(), OrderRequest.STATUS_PENDING)
            .stream()
            .map(request -> toPendingRequest(request, order, currentUserId))
            .toList();
        return new OrderDetailsResponse(
            order.getId(),
            order.getPublicId(),
            order.getStatus(),
            viewerIsParticipant ? (viewerIsMaker ? order.getMakerRole() : order.getTakerRole()) : null,
            viewerIsParticipant ? (viewerIsMaker ? order.getTakerRole() : order.getMakerRole()) : null,
            order.getMakerRole(),
            order.getTakerRole(),
            new OrderReadModelDtos.Game(
                order.getGameIdSnapshot(),
                order.getGameSlugSnapshot(),
                order.getGameTitleSnapshot()
            ),
            new OrderReadModelDtos.Category(
                order.getCategoryIdSnapshot(),
                order.getCategorySlugSnapshot(),
                order.getCategoryTitleSnapshot()
            ),
            new OrderReadModelDtos.Counterparty(
                viewerIsParticipant ? resolveCounterpartyUsername(order, viewerIsMaker) : null
            ),
            order.getTitleSnapshot(),
            order.getDescriptionSnapshot(),
            order.getTradeTermsSnapshot(),
            order.getOrderedQuantity(),
            order.getDeliveredQuantity(),
            new OrderReadModelDtos.Price(
                order.getDisplayUnitPriceAmount(),
                order.getDisplayTotalAmount(),
                order.getViewerCurrencyCodeSnapshot()
            ),
            toFinancialSummary(order, viewerIsMaker),
            order.getSellerGrossAmount(),
            order.getSellerFeeAmount(),
            order.getSellerNetAmount(),
            readContexts(order.getContextsSnapshot()),
            readAttributes(order.getAttributesSnapshot()),
            readDeliveryMethods(order.getDeliveryMethodsSnapshot()),
            order.getExpiresAt(),
            order.getCreatedAt(),
            order.getUpdatedAt(),
            new OrderReadModelDtos.AvailableActions(
                viewerIsMaker && order.isPending(),
                viewerIsParticipant && (order.isPending() || (viewerIsSeller && isActiveOrder(order))),
                viewerIsBuyer && isActiveOrder(order),
                viewerIsParticipant && isActiveOrder(order),
                viewerIsSeller && (order.isInProgress() || order.isPartiallyDelivered()),
                viewerIsSeller && (order.isInProgress() || order.isPartiallyDelivered()),
                viewerIsBuyer && (order.isInProgress() || order.isPartiallyDelivered() || order.isDelivered())
            ),
            pendingRequests,
            orderDisputeService.buildOrderDisputeBlock(order, principal)
        );
    }

    private MyOrdersResponse.Item toListItem(Order order, Long currentUserId) {
        boolean viewerIsMaker = isMaker(order, currentUserId);
        return new MyOrdersResponse.Item(
            order.getId(),
            order.getPublicId(),
            order.getStatus(),
            viewerIsMaker ? order.getMakerRole() : order.getTakerRole(),
            viewerIsMaker ? order.getTakerRole() : order.getMakerRole(),
            new OrderReadModelDtos.Game(
                order.getGameIdSnapshot(),
                order.getGameSlugSnapshot(),
                order.getGameTitleSnapshot()
            ),
            new OrderReadModelDtos.Category(
                order.getCategoryIdSnapshot(),
                order.getCategorySlugSnapshot(),
                order.getCategoryTitleSnapshot()
            ),
            order.getTitleSnapshot(),
            new OrderReadModelDtos.Counterparty(resolveCounterpartyUsername(order, viewerIsMaker)),
            order.getOrderedQuantity(),
            order.getDeliveredQuantity(),
            order.getDisplayUnitPriceAmount(),
            order.getDisplayTotalAmount(),
            order.getViewerCurrencyCodeSnapshot(),
            order.getSellerGrossAmount(),
            order.getSellerFeeAmount(),
            order.getSellerNetAmount(),
            toFinancialSummaryPreview(toFinancialSummary(order, viewerIsMaker)),
            order.getExpiresAt(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }

    private OrderReadModelDtos.FinancialSummary toFinancialSummary(Order order, boolean viewerIsMaker) {
        if (!viewerIsMaker) {
            return new OrderReadModelDtos.FinancialSummary(
                LABEL_DEAL_AMOUNT,
                order.getDisplayTotalAmount(),
                order.getViewerCurrencyCodeSnapshot(),
                order.getDisplayTotalAmount(),
                order.getDisplayUnitPriceAmount(),
                order.getViewerCurrencyCodeSnapshot(),
                null,
                null,
                null,
                VIEWER_PERSPECTIVE_TAKER
            );
        }

        String settlementCurrencyCode = order.getOfferPriceCurrencyCodeSnapshot();
        BigDecimal settlementUnitPriceAmount = resolveSettlementUnitPriceAmount(order);
        if (ROLE_SELLER.equals(order.getMakerRole())) {
            return new OrderReadModelDtos.FinancialSummary(
                LABEL_MAKER_SELLER_PRIMARY,
                order.getSellerNetAmount(),
                settlementCurrencyCode,
                order.getSellerGrossAmount(),
                settlementUnitPriceAmount,
                settlementCurrencyCode,
                order.getSellerFeeBpsSnapshot(),
                feeRatePercent(order.getSellerFeeBpsSnapshot()),
                order.getSellerFeeAmount(),
                VIEWER_PERSPECTIVE_MAKER
            );
        }
        if (ROLE_BUYER.equals(order.getMakerRole())) {
            return new OrderReadModelDtos.FinancialSummary(
                LABEL_MAKER_BUYER_PRIMARY,
                order.getSellerGrossAmount(),
                settlementCurrencyCode,
                order.getSellerGrossAmount(),
                settlementUnitPriceAmount,
                settlementCurrencyCode,
                null,
                null,
                null,
                VIEWER_PERSPECTIVE_MAKER
            );
        }
        throw new IllegalStateException("Unsupported order maker role: " + order.getMakerRole());
    }

    private OrderReadModelDtos.FinancialSummaryPreview toFinancialSummaryPreview(
        OrderReadModelDtos.FinancialSummary summary
    ) {
        return new OrderReadModelDtos.FinancialSummaryPreview(
            summary.primaryLabel(),
            summary.primaryAmount(),
            summary.primaryCurrencyCode(),
            summary.viewerPerspective()
        );
    }

    private BigDecimal resolveSettlementUnitPriceAmount(Order order) {
        if (order.getOfferPriceAmountSnapshot() != null) {
            return order.getOfferPriceAmountSnapshot();
        }
        if (order.getSellerGrossAmount() == null
            || order.getOrderedQuantity() == null
            || order.getOrderedQuantity().signum() == 0) {
            return null;
        }
        return order.getSellerGrossAmount()
            .divide(order.getOrderedQuantity(), ORDER_MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal feeRatePercent(int feeRateBps) {
        return BigDecimal.valueOf(feeRateBps).movePointLeft(2);
    }

    private boolean isMaker(Order order, Long currentUserId) {
        return order.getMakerUserId().equals(currentUserId);
    }

    private boolean isSeller(Order order, Long currentUserId) {
        return (ROLE_SELLER.equals(order.getMakerRole()) && order.getMakerUserId().equals(currentUserId))
            || (ROLE_SELLER.equals(order.getTakerRole()) && order.getTakerUserId().equals(currentUserId));
    }

    private boolean isBuyer(Order order, Long currentUserId) {
        return (ROLE_BUYER.equals(order.getMakerRole()) && order.getMakerUserId().equals(currentUserId))
            || (ROLE_BUYER.equals(order.getTakerRole()) && order.getTakerUserId().equals(currentUserId));
    }

    private boolean isActiveOrder(Order order) {
        return order.isInProgress() || order.isPartiallyDelivered() || order.isDelivered();
    }

    private OrderReadModelDtos.PendingRequest toPendingRequest(
        OrderRequest request,
        Order order,
        Long currentUserId
    ) {
        boolean viewerCanResolve = isParticipant(order, currentUserId)
            && !request.getRequestedByUserId().equals(currentUserId)
            && !request.getRequestedByRole().equals(resolveParticipantRole(order, currentUserId));
        return new OrderReadModelDtos.PendingRequest(
            request.getPublicId(),
            request.getRequestType(),
            request.getRequestedByUserId(),
            request.getRequestedByRole(),
            request.getRequestedQuantity(),
            request.getCreatedAt(),
            viewerCanResolve,
            viewerCanResolve
        );
    }

    private boolean isParticipant(Order order, Long currentUserId) {
        return order.getMakerUserId().equals(currentUserId) || order.getTakerUserId().equals(currentUserId);
    }

    private String resolveParticipantRole(Order order, Long currentUserId) {
        if (order.getMakerUserId().equals(currentUserId)) {
            return order.getMakerRole();
        }
        if (order.getTakerUserId().equals(currentUserId)) {
            return order.getTakerRole();
        }
        return null;
    }

    private String resolveCounterpartyUsername(Order order, boolean viewerIsMaker) {
        return viewerIsMaker ? order.getTakerUsernameSnapshot() : order.getOwnerUsernameSnapshot();
    }

    private int normalizePage(Integer page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page < 0) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_PAGE",
                "Query parameter 'page' must be greater than or equal to 0"
            );
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size <= 0) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "INVALID_PAGE_SIZE",
                "Query parameter 'size' must be greater than 0"
            );
        }
        return size;
    }

    private String normalizeRole(String role) {
        String normalized = normalizeOptionalValue(role);
        if (normalized == null) {
            return null;
        }
        if (!FILTER_ROLE_MAKER.equals(normalized) && !FILTER_ROLE_TAKER.equals(normalized)) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Query parameter 'role' must be 'maker' or 'taker'"
            );
        }
        return normalized;
    }

    private String normalizeOptionalValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private List<OrderReadModelDtos.Context> readContexts(JsonNode value) {
        return readList(value, new TypeReference<List<OrderReadModelDtos.Context>>() {
        });
    }

    private List<OrderReadModelDtos.Attribute> readAttributes(JsonNode value) {
        return readList(value, new TypeReference<List<OrderReadModelDtos.Attribute>>() {
        });
    }

    private List<OrderReadModelDtos.DeliveryMethod> readDeliveryMethods(JsonNode value) {
        return readList(value, new TypeReference<List<OrderReadModelDtos.DeliveryMethod>>() {
        });
    }

    private <T> List<T> readList(JsonNode value, TypeReference<List<T>> typeReference) {
        if (value == null || value.isNull()) {
            return List.of();
        }
        List<T> items = objectMapper.convertValue(value, typeReference);
        return items == null ? List.of() : items;
    }
}
