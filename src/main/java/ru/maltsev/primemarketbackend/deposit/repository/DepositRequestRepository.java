package ru.maltsev.primemarketbackend.deposit.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequest;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequestStatus;

public interface DepositRequestRepository extends JpaRepository<DepositRequest, Long> {
    Optional<DepositRequest> findByPublicId(UUID publicId);

    Optional<DepositRequest> findByPublicIdAndUserId(UUID publicId, Long userId);

    Page<DepositRequest> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<DepositRequest> findAllByUserIdAndStatusOrderByCreatedAtDesc(
        Long userId,
        DepositRequestStatus status,
        Pageable pageable
    );

    Page<DepositRequest> findAllByStatusOrderByCreatedAtDesc(DepositRequestStatus status, Pageable pageable);

    Page<DepositRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
