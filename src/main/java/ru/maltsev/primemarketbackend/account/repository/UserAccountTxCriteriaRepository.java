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
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.hibernate.query.criteria.JpaExpression;
import ru.maltsev.primemarketbackend.account.domain.UserAccount;
import ru.maltsev.primemarketbackend.account.domain.UserAccountTx;
import ru.maltsev.primemarketbackend.currency.domain.UserCurrencyConversion;
import ru.maltsev.primemarketbackend.deposit.domain.DepositRequest;
import ru.maltsev.primemarketbackend.withdrawal.domain.WithdrawalRequest;

@Repository
@RequiredArgsConstructor
public class UserAccountTxCriteriaRepository {
    private final EntityManager entityManager;

    public Page<UserAccountTxReadRow> findUserAccountTxs(
            Long userId,
            List<String> currency,
            List<String> type,
            String searchQuery,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<UserAccountTxReadRow> query = cb.createQuery(UserAccountTxReadRow.class);
        Root<UserAccountTx> tx = query.from(UserAccountTx.class);
        Join<UserAccountTx, UserAccount> acc = tx.join("userAccount");

        List<Predicate> predicates = buildTxPredicates(cb, query, tx, acc, userId, currency, type, searchQuery, from, to);
        query.where(predicates.toArray(new Predicate[0]));
        query.select(cb.construct(
                UserAccountTxReadRow.class,
                tx.get("id"),
                tx.get("publicId"),
                tx.get("amount"),
                acc.get("currencyCode"),
                tx.get("txType"),
                tx.get("refType"),
                tx.get("refId"),
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

        TypedQuery<UserAccountTxReadRow> typedQuery = entityManager.createQuery(query);
        int pageSize = pageable != null ? pageable.getPageSize() : 20;
        int pageNumber = pageable != null ? pageable.getPageNumber() : 0;
        typedQuery.setFirstResult(pageNumber * pageSize);
        typedQuery.setMaxResults(pageSize);
        List<UserAccountTxReadRow> content = typedQuery.getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<UserAccountTx> txCount = countQuery.from(UserAccountTx.class);
        Join<UserAccountTx, UserAccount> accCount = txCount.join("userAccount");
        List<Predicate> countPredicates = buildTxPredicates(
                cb,
                countQuery,
                txCount,
                accCount,
                userId,
                currency,
                type,
                searchQuery,
                from,
                to
        );
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
            CriteriaQuery<?> criteriaQuery,
            Root<UserAccountTx> tx,
            Join<UserAccountTx, UserAccount> acc,
            Long userId,
            List<String> currency,
            List<String> type,
            String searchQuery,
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
        String normalizedQuery = normalizeSearchQuery(searchQuery);
        if (normalizedQuery != null) {
            predicates.add(buildSearchPredicate(cb, criteriaQuery, tx, acc, normalizedQuery));
        }
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(tx.get("createdAt"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(tx.get("createdAt"), to));
        }
        return predicates;
    }

    private static Predicate buildSearchPredicate(
            CriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            Root<UserAccountTx> tx,
            Join<UserAccountTx, UserAccount> acc,
            String normalizedQuery
    ) {
        String likePattern = toLikePattern(normalizedQuery);
        List<Predicate> searchPredicates = new ArrayList<>();
        searchPredicates.add(likeText(cb, tx.get("txType"), likePattern));
        searchPredicates.add(likeText(cb, tx.get("refType"), likePattern));
        searchPredicates.add(likeText(cb, acc.get("currencyCode"), likePattern));
        searchPredicates.add(likeAsText(cb, tx.get("publicId"), likePattern));

        UUID uuidQuery = parseUuid(normalizedQuery);
        if (uuidQuery != null) {
            searchPredicates.add(cb.equal(tx.get("publicId"), uuidQuery));
            addPublicIdSubqueryPredicate(
                    cb,
                    criteriaQuery,
                    tx,
                    searchPredicates,
                    DepositRequest.class,
                    "DEPOSIT_REQUEST",
                    uuidQuery
            );
            addPublicIdSubqueryPredicate(
                    cb,
                    criteriaQuery,
                    tx,
                    searchPredicates,
                    WithdrawalRequest.class,
                    "WITHDRAWAL_REQUEST",
                    uuidQuery
            );
            addPublicIdSubqueryPredicate(
                    cb,
                    criteriaQuery,
                    tx,
                    searchPredicates,
                    UserCurrencyConversion.class,
                    "USER_CURRENCY_CONVERSION",
                    uuidQuery
            );
            Subquery<Long> orderIds = criteriaQuery.subquery(Long.class);
            Root<ru.maltsev.primemarketbackend.order.domain.Order> order =
                    orderIds.from(ru.maltsev.primemarketbackend.order.domain.Order.class);
            orderIds.select(order.get("id")).where(cb.equal(order.get("publicId"), uuidQuery));
            searchPredicates.add(cb.and(
                    cb.like(tx.get("refType"), "ORDER_%"),
                    tx.get("refId").in(orderIds)
            ));
        }

        addPublicIdTextSubqueryPredicate(
                cb,
                criteriaQuery,
                tx,
                searchPredicates,
                DepositRequest.class,
                "DEPOSIT_REQUEST",
                likePattern
        );
        addPublicIdTextSubqueryPredicate(
                cb,
                criteriaQuery,
                tx,
                searchPredicates,
                WithdrawalRequest.class,
                "WITHDRAWAL_REQUEST",
                likePattern
        );
        addPublicIdTextSubqueryPredicate(
                cb,
                criteriaQuery,
                tx,
                searchPredicates,
                UserCurrencyConversion.class,
                "USER_CURRENCY_CONVERSION",
                likePattern
        );

        addTextSubqueryPredicate(
                cb,
                criteriaQuery,
                tx,
                searchPredicates,
                DepositRequest.class,
                "DEPOSIT_REQUEST",
                likePattern,
                List.of("depositMethodTitleSnapshot", "currencyCodeSnapshot")
        );
        addTextSubqueryPredicate(
                cb,
                criteriaQuery,
                tx,
                searchPredicates,
                WithdrawalRequest.class,
                "WITHDRAWAL_REQUEST",
                likePattern,
                List.of("withdrawalMethodTitleSnapshot", "currencyCodeSnapshot")
        );
        addTextSubqueryPredicate(
                cb,
                criteriaQuery,
                tx,
                searchPredicates,
                UserCurrencyConversion.class,
                "USER_CURRENCY_CONVERSION",
                likePattern,
                List.of("fromCurrencyCode", "toCurrencyCode", "status")
        );

        Subquery<Long> orderTextIds = criteriaQuery.subquery(Long.class);
        Root<ru.maltsev.primemarketbackend.order.domain.Order> order =
                orderTextIds.from(ru.maltsev.primemarketbackend.order.domain.Order.class);
        orderTextIds.select(order.get("id")).where(cb.or(
                likeText(cb, order.get("gameTitleSnapshot"), likePattern),
                likeText(cb, order.get("categoryTitleSnapshot"), likePattern),
                likeText(cb, order.get("titleSnapshot"), likePattern),
                likeText(cb, order.get("descriptionSnapshot"), likePattern),
                likeAsText(cb, order.get("publicId"), likePattern)
        ));
        searchPredicates.add(cb.and(
                cb.like(tx.get("refType"), "ORDER_%"),
                tx.get("refId").in(orderTextIds)
        ));

        return cb.or(searchPredicates.toArray(new Predicate[0]));
    }

    private static <T> void addPublicIdSubqueryPredicate(
            CriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            Root<UserAccountTx> tx,
            List<Predicate> searchPredicates,
            Class<T> entityClass,
            String refType,
            UUID publicId
    ) {
        Subquery<Long> ids = criteriaQuery.subquery(Long.class);
        Root<T> root = ids.from(entityClass);
        ids.select(root.get("id")).where(cb.equal(root.get("publicId"), publicId));
        searchPredicates.add(cb.and(
                cb.equal(tx.get("refType"), refType),
                tx.get("refId").in(ids)
        ));
    }

    private static <T> void addPublicIdTextSubqueryPredicate(
            CriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            Root<UserAccountTx> tx,
            List<Predicate> searchPredicates,
            Class<T> entityClass,
            String refType,
            String likePattern
    ) {
        Subquery<Long> ids = criteriaQuery.subquery(Long.class);
        Root<T> root = ids.from(entityClass);
        ids.select(root.get("id")).where(likeAsText(cb, root.get("publicId"), likePattern));
        searchPredicates.add(cb.and(
                cb.equal(tx.get("refType"), refType),
                tx.get("refId").in(ids)
        ));
    }

    private static <T> void addTextSubqueryPredicate(
            CriteriaBuilder cb,
            CriteriaQuery<?> criteriaQuery,
            Root<UserAccountTx> tx,
            List<Predicate> searchPredicates,
            Class<T> entityClass,
            String refType,
            String likePattern,
            List<String> fields
    ) {
        Subquery<Long> ids = criteriaQuery.subquery(Long.class);
        Root<T> root = ids.from(entityClass);
        List<Predicate> fieldPredicates = fields.stream()
                .map(field -> likeText(cb, root.get(field), likePattern))
                .toList();
        ids.select(root.get("id")).where(cb.or(fieldPredicates.toArray(new Predicate[0])));
        searchPredicates.add(cb.and(
                cb.equal(tx.get("refType"), refType),
                tx.get("refId").in(ids)
        ));
    }

    private static Predicate likeText(CriteriaBuilder cb, Path<String> path, String likePattern) {
        return cb.like(cb.lower(cb.coalesce(path, "")), likePattern, '\\');
    }

    private static Predicate likeAsText(CriteriaBuilder cb, Path<?> path, String likePattern) {
        JpaExpression<String> textPath = ((JpaExpression<?>) path).cast(String.class);
        return cb.like(cb.lower(textPath), likePattern, '\\');
    }

    private static String normalizeSearchQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String toLikePattern(String normalizedQuery) {
        return "%" + normalizedQuery
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
