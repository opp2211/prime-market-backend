package ru.maltsev.primemarketbackend.withdrawal.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.withdrawal.domain.PayoutProfile;

public interface PayoutProfileRepository extends JpaRepository<PayoutProfile, Long> {
    List<PayoutProfile> findAllByUserIdAndActiveTrueOrderByDefaultProfileDescCreatedAtDesc(Long userId);

    Optional<PayoutProfile> findByPublicIdAndUserIdAndActiveTrue(UUID publicId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pp from PayoutProfile pp where pp.publicId = :publicId and pp.userId = :userId")
    Optional<PayoutProfile> findByPublicIdAndUserIdForUpdate(
        @Param("publicId") UUID publicId,
        @Param("userId") Long userId
    );

    boolean existsByUserIdAndWithdrawalMethodIdAndActiveTrue(Long userId, Long withdrawalMethodId);

    @Modifying
    @Query("""
        update PayoutProfile pp
        set pp.defaultProfile = false
        where pp.userId = :userId
          and pp.withdrawalMethod.id = :withdrawalMethodId
          and pp.active = true
          and pp.defaultProfile = true
          and (:excludeId is null or pp.id <> :excludeId)
        """)
    int clearDefaultForUserAndMethod(
        @Param("userId") Long userId,
        @Param("withdrawalMethodId") Long withdrawalMethodId,
        @Param("excludeId") Long excludeId
    );
}
