package ru.maltsev.primemarketbackend.order.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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

@Service
@RequiredArgsConstructor
public class OrderReadService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String FILTER_ROLE_MAKER = "maker";
    private static final String FILTER_ROLE_TAKER = "taker";

    private final OrderReadQueryRepository orderReadQueryRepository;
    private final OrderRequestRepository orderRequestRepository;
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
    public OrderDetailsResponse getOrderDetails(UUID publicOrderId, Long currentUserId) {
        Order order = orderReadQueryRepository.findParticipantOrder(publicOrderId, currentUserId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "ORDER_NOT_FOUND",
                "Order not found"
            ));

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
            viewerIsMaker ? order.getMakerRole() : order.getTakerRole(),
            viewerIsMaker ? order.getTakerRole() : order.getMakerRole(),
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
            new OrderReadModelDtos.Counterparty(resolveCounterpartyUsername(order, viewerIsMaker)),
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
                order.isPending() && viewerIsMaker,
                order.isPending() || (viewerIsSeller && isActiveOrder(order)),
                viewerIsBuyer && isActiveOrder(order),
                isActiveOrder(order),
                viewerIsSeller && (order.isInProgress() || order.isPartiallyDelivered()),
                viewerIsSeller && (order.isInProgress() || order.isPartiallyDelivered()),
                viewerIsBuyer && (order.isInProgress() || order.isPartiallyDelivered() || order.isDelivered())
            ),
            pendingRequests
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
            order.getExpiresAt(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }

    private boolean isMaker(Order order, Long currentUserId) {
        return order.getMakerUserId().equals(currentUserId);
    }

    private boolean isSeller(Order order, Long currentUserId) {
        return ("seller".equals(order.getMakerRole()) && order.getMakerUserId().equals(currentUserId))
            || ("seller".equals(order.getTakerRole()) && order.getTakerUserId().equals(currentUserId));
    }

    private boolean isBuyer(Order order, Long currentUserId) {
        return ("buyer".equals(order.getMakerRole()) && order.getMakerUserId().equals(currentUserId))
            || ("buyer".equals(order.getTakerRole()) && order.getTakerUserId().equals(currentUserId));
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
