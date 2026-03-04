package ru.maltsev.primemarketbackend.account.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import ru.maltsev.primemarketbackend.account.api.dto.UserAccountTxShortResponse;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;
import ru.maltsev.primemarketbackend.account.domain.UserAccountTx;

@Repository
@RequiredArgsConstructor
public class UserAccountTxCriteriaRepository {
    private final EntityManager entityManager;

    public Page<UserAccountTxShortResponse> findUserAccountTxs(
            Long userId,
            List<String> currency,
            List<String> type,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<UserAccountTxShortResponse> query = cb.createQuery(UserAccountTxShortResponse.class);
        Root<UserAccountTx> tx = query.from(UserAccountTx.class);
        Join<UserAccountTx, UserAccount> acc = tx.join("userAccount");

        List<Predicate> predicates = buildTxPredicates(cb, tx, acc, userId, currency, type, from, to);
        query.where(predicates.toArray(new Predicate[0]));
        query.select(cb.construct(
                UserAccountTxShortResponse.class,
                tx.get("publicId"),
                tx.get("amount"),
                acc.get("currencyCode"),
                tx.get("txType"),
                tx.get("createdAt")
        ));

        if (pageable != null && pageable.getSort().isSorted()) {
            List<Order> orders = new ArrayList<>();
            for (Sort.Order sortOrder : pageable.getSort()) {
                Path<?> sortPath = tx.get(sortOrder.getProperty());
                orders.add(sortOrder.isAscending() ? cb.asc(sortPath) : cb.desc(sortPath));
            }
            query.orderBy(orders);
        }

        TypedQuery<UserAccountTxShortResponse> typedQuery = entityManager.createQuery(query);
        int pageSize = pageable != null ? pageable.getPageSize() : 20;
        int pageNumber = pageable != null ? pageable.getPageNumber() : 0;
        typedQuery.setFirstResult(pageNumber * pageSize);
        typedQuery.setMaxResults(pageSize);
        List<UserAccountTxShortResponse> content = typedQuery.getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<UserAccountTx> txCount = countQuery.from(UserAccountTx.class);
        Join<UserAccountTx, UserAccount> accCount = txCount.join("userAccount");
        List<Predicate> countPredicates = buildTxPredicates(cb, txCount, accCount, userId, currency, type, from, to);
        countQuery.select(cb.count(txCount));
        countQuery.where(countPredicates.toArray(new Predicate[0]));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        Pageable resolvedPageable = pageable != null
                ? pageable
                : PageRequest.of(pageNumber, pageSize, Sort.unsorted());
        return new PageImpl<>(content, resolvedPageable, total);
    }

    private static List<Predicate> buildTxPredicates(
            CriteriaBuilder cb,
            Root<UserAccountTx> tx,
            Join<UserAccountTx, UserAccount> acc,
            Long userId,
            List<String> currency,
            List<String> type,
            Instant from,
            Instant to
    ) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(acc.get("user").get("id"), userId));
        if (currency != null && !currency.isEmpty()) {
            predicates.add(acc.get("currencyCode").in(currency));
        }
        if (type != null && !type.isEmpty()) {
            predicates.add(tx.get("txType").in(type));
        }
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(tx.get("createdAt"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(tx.get("createdAt"), to));
        }
        return predicates;
    }
}
