package ru.maltsev.primemarketbackend.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.notification.service.NotificationService;
import ru.maltsev.primemarketbackend.order.api.dto.OrderRequestResponse;
import ru.maltsev.primemarketbackend.order.domain.Order;
import ru.maltsev.primemarketbackend.order.domain.OrderRequest;
import ru.maltsev.primemarketbackend.order.repository.OrderRepository;
import ru.maltsev.primemarketbackend.order.repository.OrderRequestRepository;

@Service
@RequiredArgsConstructor
public class OrderRequestService {
    private static final String ROLE_BUYER = "buyer";

    private final OrderRepository orderRepository;
    private final OrderRequestRepository orderRequestRepository;
    private final OrderLifecycleService orderLifecycleService;
    private final OrderEventWriteService orderEventWriteService;
    private final OrderQuantityAmendService orderQuantityAmendService;
    private final NotificationService notificationService;

    @Transactional
    public OrderRequestResponse requestCancel(UUID publicOrderId, Long actorUserId) {
        Order order = loadOrderForUpdate(publicOrderId);
        requireParticipant(order, actorUserId);
        validateActiveOrderForRequest(order);

        String actorRole = resolveParticipantRole(order, actorUserId);
        if (!ROLE_BUYER.equals(actorRole)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                "ONLY_BUYER_CAN_REQUEST_CANCEL",
                "Only buyer can request active order cancellation"
            );
        }

        OrderRequest existing = findPendingRequest(order.getId(), OrderRequest.TYPE_CANCEL);
        if (existing != null) {
            return OrderRequestResponse.from(existing);
        }

        OrderRequest request = orderRequestRepository.saveAndFlush(new OrderRequest(
            UUID.randomUUID(),
            order.getId(),
            OrderRequest.TYPE_CANCEL,
            actorUserId,
            actorRole,
            null,
            null
        ));
        orderEventWriteService.recordCancelRequested(order, request.getId(), actorUserId, actorRole);
        notificationService.notifyOrderRequestCreated(order, request, resolveCounterpartyUserId(order, actorUserId));
        return OrderRequestResponse.from(request);
    }

    @Transactional
    public OrderRequestResponse requestAmendQuantity(UUID publicOrderId, Long actorUserId, BigDecimal quantity) {
        Order order = loadOrderForUpdate(publicOrderId);
        requireParticipant(order, actorUserId);
        validateActiveOrderForRequest(order);

        BigDecimal requestedQuantity = orderQuantityAmendService.normalizeRequestedQuantity(quantity);
        orderQuantityAmendService.validateRequestedQuantity(order, requestedQuantity);

        String actorRole = resolveParticipantRole(order, actorUserId);
        OrderRequest existing = findPendingRequest(order.getId(), OrderRequest.TYPE_AMEND_QUANTITY);
        if (existing != null) {
            return OrderRequestResponse.from(existing);
        }

        OrderRequest request = orderRequestRepository.saveAndFlush(new OrderRequest(
            UUID.randomUUID(),
            order.getId(),
            OrderRequest.TYPE_AMEND_QUANTITY,
            actorUserId,
            actorRole,
            null,
            requestedQuantity
        ));
        orderEventWriteService.recordAmendQuantityRequested(
            order,
            request.getId(),
            actorUserId,
            actorRole,
            requestedQuantity
        );
        notificationService.notifyOrderRequestCreated(order, request, resolveCounterpartyUserId(order, actorUserId));
        return OrderRequestResponse.from(request);
    }

    @Transactional
    public OrderRequestResponse approve(UUID publicRequestId, Long actorUserId) {
        OrderRequest request = loadRequestForUpdate(publicRequestId);
        ensurePending(request);
        Order order = loadOrderByIdForUpdate(request.getOrderId());
        String actorRole = requireCounterparty(order, request, actorUserId);

        if (request.isCancelRequest()) {
            approveCancelRequest(order, request, actorUserId, actorRole);
        } else if (request.isAmendQuantityRequest()) {
            approveAmendQuantityRequest(order, request, actorUserId, actorRole);
        } else {
            throw invalidOrderStatus("Unsupported order request type " + request.getRequestType());
        }

        notificationService.notifyOrderRequestResolved(order, request);
        if (request.isCancelRequest()) {
            notificationService.notifyOrderStatusChanged(order, java.util.List.of(request.getRequestedByUserId()));
        }

        return OrderRequestResponse.from(request);
    }

    @Transactional
    public OrderRequestResponse reject(UUID publicRequestId, Long actorUserId) {
        OrderRequest request = loadRequestForUpdate(publicRequestId);
        ensurePending(request);
        Order order = loadOrderByIdForUpdate(request.getOrderId());
        String actorRole = requireCounterparty(order, request, actorUserId);

        Instant now = Instant.now();
        request.markRejected(actorUserId, now);
        if (request.isCancelRequest()) {
            orderEventWriteService.recordCancelRequestRejected(order, request.getId(), actorUserId, actorRole);
        } else if (request.isAmendQuantityRequest()) {
            orderEventWriteService.recordAmendQuantityRejected(order, request.getId(), actorUserId, actorRole);
        }
        notificationService.notifyOrderRequestResolved(order, request);
        return OrderRequestResponse.from(request);
    }

    private void approveCancelRequest(
        Order order,
        OrderRequest request,
        Long actorUserId,
        String actorRole
    ) {
        validateActiveOrderForRequest(order);
        Instant now = Instant.now();
        orderLifecycleService.cancelOrderAndReleaseResources(order, now);
        request.markApproved(actorUserId, now);
        cancelOtherPendingRequests(order.getId(), request.getId(), actorUserId, now);
        orderEventWriteService.recordCancelRequestApproved(order, request.getId(), actorUserId, actorRole);
    }

    private void approveAmendQuantityRequest(
        Order order,
        OrderRequest request,
        Long actorUserId,
        String actorRole
    ) {
        validateActiveOrderForRequest(order);
        BigDecimal requestedQuantity = orderQuantityAmendService.normalizeRequestedQuantity(request.getRequestedQuantity());
        OrderQuantityAmendService.AmendResult amendResult = orderQuantityAmendService.amendQuantity(
            order,
            requestedQuantity
        );

        Instant now = Instant.now();
        request.markApproved(actorUserId, now);
        orderEventWriteService.recordAmendQuantityApproved(
            order,
            request.getId(),
            actorUserId,
            actorRole,
            amendResult.previousQuantity()
        );
    }

    private Order loadOrderForUpdate(UUID publicOrderId) {
        return orderRepository.findByPublicIdForUpdate(publicOrderId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "ORDER_NOT_FOUND",
                "Order not found"
            ));
    }

    private Order loadOrderByIdForUpdate(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "ORDER_NOT_FOUND",
                "Order not found"
            ));
    }

    private OrderRequest loadRequestForUpdate(UUID publicRequestId) {
        return orderRequestRepository.findByPublicIdForUpdate(publicRequestId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "ORDER_REQUEST_NOT_FOUND",
                "Order request not found"
            ));
    }

    private void ensurePending(OrderRequest request) {
        if (!request.isPending()) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                "ORDER_REQUEST_ALREADY_RESOLVED",
                "Order request is already resolved"
            );
        }
    }

    private void requireParticipant(Order order, Long actorUserId) {
        if (isParticipant(order, actorUserId)) {
            return;
        }
        throw new ApiProblemException(
            HttpStatus.FORBIDDEN,
            "ONLY_ORDER_PARTICIPANT_CAN_REQUEST_CHANGE",
            "Only order participant can request order changes"
        );
    }

    private String requireCounterparty(Order order, OrderRequest request, Long actorUserId) {
        if (!isParticipant(order, actorUserId)) {
            throw onlyCounterpartyCanResolve();
        }
        String actorRole = resolveParticipantRole(order, actorUserId);
        if (actorUserId.equals(request.getRequestedByUserId()) || actorRole.equals(request.getRequestedByRole())) {
            throw onlyCounterpartyCanResolve();
        }
        return actorRole;
    }

    private ApiProblemException onlyCounterpartyCanResolve() {
        return new ApiProblemException(
            HttpStatus.FORBIDDEN,
            "ONLY_COUNTERPARTY_CAN_APPROVE_REQUEST",
            "Only counterparty can resolve order request"
        );
    }

    private void validateActiveOrderForRequest(Order order) {
        if (order.isInProgress() || order.isPartiallyDelivered() || order.isDelivered()) {
            return;
        }
        throw invalidOrderStatus("Order request is not allowed from status " + order.getStatus());
    }

    private OrderRequest findPendingRequest(Long orderId, String requestType) {
        return orderRequestRepository.findPendingByOrderIdAndRequestTypeForUpdate(
            orderId,
            requestType,
            OrderRequest.STATUS_PENDING
        ).orElse(null);
    }

    private void cancelOtherPendingRequests(Long orderId, Long approvedRequestId, Long actorUserId, Instant now) {
        for (OrderRequest pendingRequest : orderRequestRepository.findAllByOrderIdAndStatusOrderByCreatedAtDesc(
            orderId,
            OrderRequest.STATUS_PENDING
        )) {
            if (!pendingRequest.getId().equals(approvedRequestId)) {
                pendingRequest.markCanceled(actorUserId, now);
            }
        }
    }

    private boolean isParticipant(Order order, Long actorUserId) {
        return order.getMakerUserId().equals(actorUserId) || order.getTakerUserId().equals(actorUserId);
    }

    private Long resolveCounterpartyUserId(Order order, Long actorUserId) {
        if (order.getMakerUserId().equals(actorUserId)) {
            return order.getTakerUserId();
        }
        if (order.getTakerUserId().equals(actorUserId)) {
            return order.getMakerUserId();
        }
        throw invalidOrderStatus("Order counterparty is not defined");
    }

    private String resolveParticipantRole(Order order, Long actorUserId) {
        if (order.getMakerUserId().equals(actorUserId)) {
            return order.getMakerRole();
        }
        if (order.getTakerUserId().equals(actorUserId)) {
            return order.getTakerRole();
        }
        throw invalidOrderStatus("Order participant role not found");
    }

    private ApiProblemException invalidOrderStatus(String message) {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            "INVALID_ORDER_STATUS",
            message
        );
    }
}
