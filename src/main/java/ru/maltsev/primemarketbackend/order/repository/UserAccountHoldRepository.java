package ru.maltsev.primemarketbackend.order.repository;

import java.util.Optional;
import java.util.List;
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

    @Query("""
        select new ru.maltsev.primemarketbackend.order.repository.WalletReserveReadRow(
            case
                when h.refType = 'order' then 'ORDER'
                when h.refType = 'offer' then 'OFFER'
                else h.refType
            end,
            o.publicId,
            h.refId,
            coalesce(o.titleSnapshot, offer.title, h.reason),
            case
                when o.id is not null then concat(o.gameTitleSnapshot, ', ', o.categoryTitleSnapshot)
                when offer.id is not null then 'Активное предложение на покупку'
                else h.reason
            end,
            h.amount,
            ua.currencyCode,
            h.status,
            h.createdAt
        )
        from UserAccountHold h
        join ru.maltsev.primemarketbackend.account.domain.UserAccount ua on ua.id = h.userAccountId
        left join ru.maltsev.primemarketbackend.order.domain.Order o
            on h.refType = 'order' and o.id = h.refId
        left join ru.maltsev.primemarketbackend.offer.domain.Offer offer
            on h.refType = 'offer' and offer.id = h.refId
        where ua.user.id = :userId
          and h.status = 'active'
        order by h.createdAt desc
        """)
    List<WalletReserveReadRow> findActiveWalletReserveRowsByUserId(@Param("userId") Long userId);
}
