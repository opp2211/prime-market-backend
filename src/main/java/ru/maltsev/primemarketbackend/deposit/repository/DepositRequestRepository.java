package ru.maltsev.primemarketbackend.deposit.repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequest;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequestStatus;

public interface DepositRequestRepository extends JpaRepository<DepositRequest, Long> {
    Optional<DepositRequest> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dr from DepositRequest dr where dr.publicId = :publicId")
    Optional<DepositRequest> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    Optional<DepositRequest> findByPublicIdAndUserId(UUID publicId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dr from DepositRequest dr where dr.publicId = :publicId and dr.userId = :userId")
    Optional<DepositRequest> findByPublicIdAndUserIdForUpdate(
        @Param("publicId") UUID publicId,
        @Param("userId") Long userId
    );

    Page<DepositRequest> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<DepositRequest> findAllByUserIdAndStatusOrderByCreatedAtDesc(
        Long userId,
        DepositRequestStatus status,
        Pageable pageable
    );

    Page<DepositRequest> findAllByStatusOrderByCreatedAtDesc(DepositRequestStatus status, Pageable pageable);

    Page<DepositRequest> findAllByStatusInOrderByCreatedAtDesc(
        Set<DepositRequestStatus> statuses,
        Pageable pageable
    );

    Page<DepositRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(
        value = """
            select new ru.maltsev.primemarketbackend.deposit.repository.AdminDepositRequestQueueRow(
                dr.publicId,
                dr.amount,
                dr.currencyCodeSnapshot,
                dm.id,
                dr.depositMethodTitleSnapshot,
                dr.status,
                dr.userId,
                u.username,
                dr.detailsIssuedAt,
                dr.userMarkedPaidAt,
                dr.confirmedAt,
                dr.rejectedAt,
                dr.cancelledAt,
                dr.createdAt,
                dr.updatedAt
            )
            from DepositRequest dr
            join dr.depositMethod dm,
                 User u
            where u.id = dr.userId
            """,
        countQuery = """
            select count(dr)
            from DepositRequest dr,
                 User u
            where u.id = dr.userId
            """
    )
    Page<AdminDepositRequestQueueRow> findAdminQueueRows(Pageable pageable);

    @Query(
        value = """
            select new ru.maltsev.primemarketbackend.deposit.repository.AdminDepositRequestQueueRow(
                dr.publicId,
                dr.amount,
                dr.currencyCodeSnapshot,
                dm.id,
                dr.depositMethodTitleSnapshot,
                dr.status,
                dr.userId,
                u.username,
                dr.detailsIssuedAt,
                dr.userMarkedPaidAt,
                dr.confirmedAt,
                dr.rejectedAt,
                dr.cancelledAt,
                dr.createdAt,
                dr.updatedAt
            )
            from DepositRequest dr
            join dr.depositMethod dm,
                 User u
            where u.id = dr.userId
              and dr.status in :statuses
            """,
        countQuery = """
            select count(dr)
            from DepositRequest dr,
                 User u
            where u.id = dr.userId
              and dr.status in :statuses
            """
    )
    Page<AdminDepositRequestQueueRow> findAdminQueueRowsByStatusIn(
        @Param("statuses") Set<DepositRequestStatus> statuses,
        Pageable pageable
    );
}
