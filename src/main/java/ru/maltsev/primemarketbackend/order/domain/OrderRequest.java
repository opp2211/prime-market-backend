package ru.maltsev.primemarketbackend.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "order_requests")
public class OrderRequest {
    public static final String TYPE_CANCEL = "cancel";
    public static final String TYPE_AMEND_QUANTITY = "amend_quantity";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_CANCELED = "canceled";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "request_type", nullable = false, length = 32)
    private String requestType;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(name = "requested_by_role", nullable = false, length = 8)
    private String requestedByRole;

    @Column(nullable = false, length = 16)
    private String status;

    @Column
    private String reason;

    @Column(name = "requested_quantity", precision = 18, scale = 8)
    private BigDecimal requestedQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by_user_id")
    private Long resolvedByUserId;

    public OrderRequest(
        UUID publicId,
        Long orderId,
        String requestType,
        Long requestedByUserId,
        String requestedByRole,
        String reason,
        BigDecimal requestedQuantity
    ) {
        this.publicId = publicId;
        this.orderId = orderId;
        this.requestType = requestType;
        this.requestedByUserId = requestedByUserId;
        this.requestedByRole = requestedByRole;
        this.status = STATUS_PENDING;
        this.reason = reason;
        this.requestedQuantity = requestedQuantity;
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public boolean isCancelRequest() {
        return TYPE_CANCEL.equals(requestType);
    }

    public boolean isAmendQuantityRequest() {
        return TYPE_AMEND_QUANTITY.equals(requestType);
    }

    public void markApproved(Long resolvedByUserId, Instant resolvedAt) {
        this.status = STATUS_APPROVED;
        this.resolvedByUserId = resolvedByUserId;
        this.resolvedAt = resolvedAt;
    }

    public void markRejected(Long resolvedByUserId, Instant resolvedAt) {
        this.status = STATUS_REJECTED;
        this.resolvedByUserId = resolvedByUserId;
        this.resolvedAt = resolvedAt;
    }

    public void markCanceled(Long resolvedByUserId, Instant resolvedAt) {
        this.status = STATUS_CANCELED;
        this.resolvedByUserId = resolvedByUserId;
        this.resolvedAt = resolvedAt;
    }
}
