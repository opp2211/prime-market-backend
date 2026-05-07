package ru.maltsev.primemarketbackend.withdrawal.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequest;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequestStatus;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {
    Optional<WithdrawalRequest> findByPublicCode(String publicCode);

    Optional<WithdrawalRequest> findByPublicCodeAndUserId(String publicCode, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wr from WithdrawalRequest wr where wr.publicCode = :publicCode")
    Optional<WithdrawalRequest> findByPublicCodeForUpdate(@Param("publicCode") String publicCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wr from WithdrawalRequest wr where wr.publicCode = :publicCode and wr.userId = :userId")
    Optional<WithdrawalRequest> findByPublicCodeAndUserIdForUpdate(
        @Param("publicCode") String publicCode,
        @Param("userId") Long userId
    );

    Page<WithdrawalRequest> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<WithdrawalRequest> findAllByUserIdAndStatusOrderByCreatedAtDesc(
        Long userId,
        WithdrawalRequestStatus status,
        Pageable pageable
    );

    List<WithdrawalRequest> findTop10ByUserIdAndStatusInOrderByCreatedAtDesc(
        Long userId,
        Collection<WithdrawalRequestStatus> statuses
    );

    Page<WithdrawalRequest> findAllByStatusInOrderByCreatedAtDesc(
        Set<WithdrawalRequestStatus> statuses,
        Pageable pageable
    );

    Page<WithdrawalRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
