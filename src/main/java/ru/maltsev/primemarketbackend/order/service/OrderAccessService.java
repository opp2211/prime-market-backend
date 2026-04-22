package ru.maltsev.primemarketbackend.order.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.maltsev.primemarketbackend.exception.ApiProblemException;
import ru.maltsev.primemarketbackend.order.domain.Order;
import ru.maltsev.primemarketbackend.order.repository.OrderRepository;
import ru.maltsev.primemarketbackend.security.PermissionCodes;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;

@Service
@RequiredArgsConstructor
public class OrderAccessService {
    private static final String ROLE_BUYER = "buyer";
    private static final String ROLE_SELLER = "seller";

    private final OrderRepository orderRepository;

    public Order requireReadableOrder(UUID publicOrderId, UserPrincipal principal) {
        Order order = loadOrder(publicOrderId);
        Long currentUserId = principal.getUser().getId();
        if (isParticipant(order, currentUserId) || principal.hasAuthority(PermissionCodes.ORDERS_VIEW_ANY)) {
            return order;
        }
        throw orderNotFound();
    }

    public Order loadOrder(UUID publicOrderId) {
        return orderRepository.findByPublicId(publicOrderId)
            .orElseThrow(this::orderNotFound);
    }

    public boolean isParticipant(Order order, Long currentUserId) {
        return order.getMakerUserId().equals(currentUserId) || order.getTakerUserId().equals(currentUserId);
    }

    public boolean isBuyer(Order order, Long currentUserId) {
        return ROLE_BUYER.equals(resolveParticipantRole(order, currentUserId));
    }

    public boolean isSeller(Order order, Long currentUserId) {
        return ROLE_SELLER.equals(resolveParticipantRole(order, currentUserId));
    }

    public String resolveParticipantRole(Order order, Long currentUserId) {
        if (order.getMakerUserId().equals(currentUserId)) {
            return order.getMakerRole();
        }
        if (order.getTakerUserId().equals(currentUserId)) {
            return order.getTakerRole();
        }
        return null;
    }

    public Long resolveBuyerUserId(Order order) {
        if (ROLE_BUYER.equals(order.getMakerRole())) {
            return order.getMakerUserId();
        }
        if (ROLE_BUYER.equals(order.getTakerRole())) {
            return order.getTakerUserId();
        }
        throw invalidOrder("Order buyer is not defined");
    }

    public Long resolveSellerUserId(Order order) {
        if (ROLE_SELLER.equals(order.getMakerRole())) {
            return order.getMakerUserId();
        }
        if (ROLE_SELLER.equals(order.getTakerRole())) {
            return order.getTakerUserId();
        }
        throw invalidOrder("Order seller is not defined");
    }

    public String resolveBuyerUsername(Order order) {
        return ROLE_BUYER.equals(order.getMakerRole())
            ? order.getOwnerUsernameSnapshot()
            : order.getTakerUsernameSnapshot();
    }

    public String resolveSellerUsername(Order order) {
        return ROLE_SELLER.equals(order.getMakerRole())
            ? order.getOwnerUsernameSnapshot()
            : order.getTakerUsernameSnapshot();
    }

    private ApiProblemException orderNotFound() {
        return new ApiProblemException(
            HttpStatus.NOT_FOUND,
            "ORDER_NOT_FOUND",
            "Order not found"
        );
    }

    private ApiProblemException invalidOrder(String message) {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            "INVALID_ORDER_STATE",
            message
        );
    }
}
