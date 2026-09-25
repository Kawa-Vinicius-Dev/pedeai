package com.pedeai.order.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

/** Contador do número curto do pedido por loja e dia operacional. SQL simples, igual no H2 e no PostgreSQL. */
@Repository
public class OrderNumberCounterRepository {
    private final JdbcTemplate jdbc;

    public OrderNumberCounterRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Soma 1 e trava a linha até o fim da transação. @return linhas alteradas (0 se o dia ainda não tem contador) */
    public int increment(UUID storeId, LocalDate businessDate) {
        return jdbc.update(
                "UPDATE order_number_counter SET last_number = last_number + 1 WHERE store_id = ? AND business_date = ?",
                storeId, businessDate);
    }

    public void insertZero(UUID storeId, LocalDate businessDate) {
        jdbc.update("INSERT INTO order_number_counter (store_id, business_date, last_number) VALUES (?, ?, 0)",
                storeId, businessDate);
    }

    public int current(UUID storeId, LocalDate businessDate) {
        Integer number = jdbc.queryForObject(
                "SELECT last_number FROM order_number_counter WHERE store_id = ? AND business_date = ?",
                Integer.class, storeId, businessDate);
        return number == null ? 0 : number;
    }
}
