package ru.maltsev.primemarketbackend.withdrawal.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequest;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequestStatus;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {
    Optional<WithdrawalRequest> findByPublicId(UUID publicId);

    Optional<WithdrawalRequest> findByPublicIdAndUserId(UUID publicId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wr from WithdrawalRequest wr where wr.publicId = :publicId")
    Optional<WithdrawalRequest> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wr from WithdrawalRequest wr where wr.publicId = :publicId and wr.userId = :userId")
    Optional<WithdrawalRequest> findByPublicIdAndUserIdForUpdate(
        @Param("publicId") UUID publicId,
        @Param("userId") Long userId
    );

    Page<WithdrawalRequest> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<WithdrawalRequest> findAllByUserIdAndStatusOrderByCreatedAtDesc(
        Long userId,
        WithdrawalRequestStatus status,
        Pageable pageable
    );

    Page<WithdrawalRequest> findAllByStatusInOrderByCreatedAtDesc(
        Set<WithdrawalRequestStatus> statuses,
        Pageable pageable
    );

    Page<WithdrawalRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
