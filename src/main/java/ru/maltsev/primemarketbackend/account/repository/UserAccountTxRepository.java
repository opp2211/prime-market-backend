package ru.maltsev.primemarketbackend.account.repository;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.maltsev.primemarketbackend.account.api.dto.UserAccountTxResponse;
import ru.maltsev.primemarketbackend.account.domain.UserAccountTx;

public interface UserAccountTxRepository extends JpaRepository<UserAccountTx, Long> {
    @Query("""
        select new ru.maltsev.primemarketbackend.account.api.dto.UserAccountTxResponse(
            tx.createdAt,
            tx.txType,
            tx.amount,
            acc.currencyCode,
            tx.publicId
        )
        from UserAccountTx tx
        join tx.userAccount acc
        where acc.user.id = :userId
          and (:currency is null or acc.currencyCode = :currency)
          and (:type is null or tx.txType = :type)
          and (:from is null or tx.createdAt >= :from)
          and (:to is null or tx.createdAt <= :to)
        """)
    Page<UserAccountTxResponse> findUserAccountTxs(
            @Param("userId") Long userId,
            @Param("currency") String currency,
            @Param("type") String type,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );
}
