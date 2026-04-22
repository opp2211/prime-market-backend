package ru.maltsev.primemarketbackend.order.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import ru.maltsev.primemarketbackend.order.domain.Order;

@Repository
@RequiredArgsConstructor
public class OrderReadQueryRepository {
    private static final String ROLE_MAKER = "maker";
    private static final String ROLE_TAKER = "taker";

    private final EntityManager entityManager;

    public Page<Order> findMyOrders(Long userId, String status, String role, int page, int size) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Order> query = cb.createQuery(Order.class);
        Root<Order> root = query.from(Order.class);
        List<Predicate> predicates = buildPredicates(cb, root, userId, status, role, null);
        query.select(root);
        query.where(predicates.toArray(Predicate[]::new));
        query.orderBy(
            cb.desc(root.get("createdAt")),
            cb.desc(root.get("updatedAt")),
            cb.desc(root.get("id"))
        );

        TypedQuery<Order> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(page * size);
        typedQuery.setMaxResults(size);
        List<Order> items = typedQuery.getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Order> countRoot = countQuery.from(Order.class);
        List<Predicate> countPredicates = buildPredicates(cb, countRoot, userId, status, role, null);
        countQuery.select(cb.count(countRoot));
        countQuery.where(countPredicates.toArray(Predicate[]::new));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(items, PageRequest.of(page, size), total);
    }

    private List<Predicate> buildPredicates(
        CriteriaBuilder cb,
        Root<Order> root,
        Long userId,
        String status,
        String role,
        UUID publicOrderId
    ) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(buildRolePredicate(cb, root, userId, role));
        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }
        if (publicOrderId != null) {
            predicates.add(cb.equal(root.get("publicId"), publicOrderId));
        }
        return predicates;
    }

    private Predicate buildRolePredicate(CriteriaBuilder cb, Root<Order> root, Long userId, String role) {
        if (ROLE_MAKER.equals(role)) {
            return cb.equal(root.get("makerUserId"), userId);
        }
        if (ROLE_TAKER.equals(role)) {
            return cb.equal(root.get("takerUserId"), userId);
        }
        return cb.or(
            cb.equal(root.get("makerUserId"), userId),
            cb.equal(root.get("takerUserId"), userId)
        );
    }
}
