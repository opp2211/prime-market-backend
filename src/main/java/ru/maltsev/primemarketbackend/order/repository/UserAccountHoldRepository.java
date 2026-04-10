package ru.maltsev.primemarketbackend.order.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.order.domain.UserAccountHold;

public interface UserAccountHoldRepository extends JpaRepository<UserAccountHold, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select h
        from UserAccountHold h
        where h.refType = :refType
          and h.refId = :refId
          and h.reason = :reason
        """)
    Optional<UserAccountHold> findByRefForUpdate(
        @Param("refType") String refType,
        @Param("refId") Long refId,
        @Param("reason") String reason
    );

    @Query("""
        select h
        from UserAccountHold h
        where h.refType = :refType
          and h.refId = :refId
          and h.reason = :reason
        """)
    Optional<UserAccountHold> findByRef(
        @Param("refType") String refType,
        @Param("refId") Long refId,
        @Param("reason") String reason
    );
}
