package com.pedeai.order.repository;

import com.pedeai.order.domain.Order;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.domain.OrderType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Filtros do histórico de pedidos. Filtro nulo não restringe. */
public final class OrderSpecifications {
    private OrderSpecifications() {
    }

    /** @param term número do pedido, ou parte do nome ou do telefone do cliente */
    public static Specification<Order> matching(UUID storeId, LocalDate businessDate, OrderStatus status,
                                                OrderType type, String term) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("storeId"), storeId));
            if (businessDate != null) {
                predicates.add(builder.equal(root.get("businessDate"), businessDate));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates.add(builder.equal(root.get("type"), type));
            }
            if (term != null) {
                if (term.matches("\\d{1,6}")) {
                    predicates.add(builder.equal(root.get("number"), Integer.parseInt(term)));
                } else {
                    Predicate byName = builder.like(builder.lower(root.get("customerName")),
                            "%" + term.toLowerCase(Locale.ROOT) + "%");
                    String digits = term.replaceAll("\\D", "");
                    // Sem dígitos, o padrão do telefone ("%%") casaria com todos os pedidos.
                    predicates.add(digits.isEmpty() ? byName
                            : builder.or(byName, builder.like(root.get("customerPhone"), "%" + digits + "%")));
                }
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
