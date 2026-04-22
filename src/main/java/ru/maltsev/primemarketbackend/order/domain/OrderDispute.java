package ru.maltsev.primemarketbackend.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "order_disputes")
public class OrderDispute {
    public static final String STATUS_OPEN = "open";
    public static final String STATUS_IN_REVIEW = "in_review";
    public static final String STATUS_RESOLVED = "resolved";
    public static final String STATUS_CLOSED = "closed";

    public static final String ROLE_BUYER = "buyer";
    public static final String ROLE_SELLER = "seller";
    public static final String ROLE_SUPPORT = "support";

    public static final String RESOLUTION_FORCE_CANCEL = "force_cancel";
    public static final String RESOLUTION_FORCE_COMPLETE = "force_complete";
    public static final String RESOLUTION_FORCE_AMEND_QUANTITY_AND_COMPLETE = "force_amend_quantity_and_complete";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "opened_by_user_id", nullable = false)
    private Long openedByUserId;

    @Column(name = "opened_by_role", nullable = false, length = 16)
    private String openedByRole;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(nullable = false)
    private String description;

    @Column(name = "assigned_support_user_id")
    private Long assignedSupportUserId;

    @Column(name = "taken_at")
    private Instant takenAt;

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

    @Column(name = "resolution_type", length = 64)
    private String resolutionType;

    @Column(name = "resolution_note")
    private String resolutionNote;

    public OrderDispute(
        UUID publicId,
        Long orderId,
        Long openedByUserId,
        String openedByRole,
        String reasonCode,
        String description
    ) {
        this.publicId = publicId;
        this.orderId = orderId;
        this.openedByUserId = openedByUserId;
        this.openedByRole = openedByRole;
        this.status = STATUS_OPEN;
        this.reasonCode = reasonCode;
        this.description = description;
    }

    public boolean isOpen() {
        return STATUS_OPEN.equals(status);
    }

    public boolean isInReview() {
        return STATUS_IN_REVIEW.equals(status);
    }

    public boolean isResolved() {
        return STATUS_RESOLVED.equals(status);
    }

    public boolean isActive() {
        return STATUS_OPEN.equals(status) || STATUS_IN_REVIEW.equals(status);
    }

    public void markTakenInWork(Long supportUserId, Instant takenAt) {
        this.assignedSupportUserId = supportUserId;
        this.takenAt = takenAt;
        this.status = STATUS_IN_REVIEW;
    }

    public void markResolved(
        Long resolvedByUserId,
        Instant resolvedAt,
        String resolutionType,
        String resolutionNote
    ) {
        this.status = STATUS_RESOLVED;
        this.resolvedByUserId = resolvedByUserId;
        this.resolvedAt = resolvedAt;
        this.resolutionType = resolutionType;
        this.resolutionNote = resolutionNote;
    }
}
