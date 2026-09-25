package com.pedeai;

import com.pedeai.order.service.OrderNumberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Vários caixas lançando o primeiro pedido do dia ao mesmo tempo: cada um ganha um número diferente. */
@SpringBootTest
class OrderNumberConcurrencyTest {
    private static final int ORDERS = 8;

    @Autowired
    private OrderNumberService numberService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void simultaneousOrdersNeverShareANumber() throws Exception {
        UUID store = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO store (id, name, timezone, business_day_cutoff, service_fee_bp, auto_confirm_own_orders,
                                   start_preparation_on_confirm, created_at, updated_at, version)
                VALUES (?, 'Loja movimentada', 'America/Sao_Paulo', '05:00:00', 1000, TRUE, FALSE, ?, ?, 0)""",
                store, now, now);
        LocalDate day = LocalDate.of(2026, 9, 25);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch start = new CountDownLatch(1);

        List<Integer> numbers = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(ORDERS)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < ORDERS; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return transaction.execute(status -> numberService.next(store, day));
                }));
            }
            start.countDown();
            for (Future<Integer> future : futures) {
                numbers.add(future.get());
            }
        }

        assertThat(numbers).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, ORDERS).boxed().toList());
    }
}
