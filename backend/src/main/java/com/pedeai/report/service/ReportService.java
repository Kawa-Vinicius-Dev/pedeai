package com.pedeai.report.service;

import com.pedeai.order.domain.BusinessDay;
import com.pedeai.report.dto.DashboardResponse;
import com.pedeai.report.dto.PaymentMethodRevenueResponse;
import com.pedeai.report.dto.RevenueGroupResponse;
import com.pedeai.report.dto.RevenueResponse;
import com.pedeai.report.dto.RevenueSummaryResponse;
import com.pedeai.report.dto.TopProductResponse;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.store.dto.StoreResponse;
import com.pedeai.store.service.StoreService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Faturamento e dashboard (docs/02-arquitetura.md: módulo {@code report}). Só leitura, SQL direto nas tabelas de
 * pedidos e pagamentos. O período é por dia operacional ({@code business_date}), o mesmo do número do pedido.
 */
@Service
public class ReportService {
    static final int MAX_DAYS = 366;
    static final int TOP_PRODUCTS = 10;
    private static final String PERIOD = " o.store_id = ? and o.business_date between ? and ? ";

    private final JdbcTemplate jdbc;
    private final StoreService storeService;
    private final Clock clock;

    public ReportService(JdbcTemplate jdbc, StoreService storeService, Clock clock) {
        this.jdbc = jdbc;
        this.storeService = storeService;
        this.clock = clock;
    }

    /** O dia operacional de agora na loja: às 01:30 com virada às 05:00 ainda é ontem. */
    @Transactional(readOnly = true)
    public LocalDate today(UUID storeId) {
        StoreResponse store = storeService.get(storeId);
        return BusinessDay.of(Instant.now(clock), ZoneId.of(store.timezone()), store.businessDayCutoff());
    }

    @Transactional(readOnly = true)
    public RevenueResponse revenue(UUID storeId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("A data final deve ser igual ou depois da inicial.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new BusinessRuleException("Escolha um período de até " + MAX_DAYS + " dias.");
        }
        Object[] period = {storeId, Date.valueOf(from), Date.valueOf(to)};
        return new RevenueResponse(from, to, summary(period), groups("o.business_date", period),
                groups("o.source", period), groups("o.type", period), byPaymentMethod(period));
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(UUID storeId, LocalDate date) {
        Object[] day = {storeId, Date.valueOf(date), Date.valueOf(date)};
        ZoneId zone = ZoneId.of(storeService.get(storeId).timezone());

        int[] byHour = new int[24];
        List<Long> preparation = new ArrayList<>();
        jdbc.query("select o.created_at, o.confirmed_at, o.ready_at from orders o where" + PERIOD
                + "and o.status <> 'CANCELLED'", rs -> {
            byHour[rs.getTimestamp("created_at").toInstant().atZone(zone).getHour()]++;
            Timestamp confirmed = rs.getTimestamp("confirmed_at");
            Timestamp ready = rs.getTimestamp("ready_at");
            if (confirmed != null && ready != null && !ready.before(confirmed)) {
                preparation.add(Duration.between(confirmed.toInstant(), ready.toInstant()).toSeconds());
            }
        }, day);
        Long averagePreparation = preparation.isEmpty() ? null
                : Math.round(preparation.stream().mapToLong(Long::longValue).average().orElse(0));

        List<TopProductResponse> top = jdbc.query("select i.name, sum(i.quantity) as quantity, "
                        + "sum(i.total_cents) as total from order_item i join orders o on o.id = i.order_id where"
                        + PERIOD + "and o.status <> 'CANCELLED' and i.status = 'ACTIVE' "
                        + "group by i.name order by quantity desc, total desc, i.name",
                (rs, row) -> new TopProductResponse(rs.getString("name"), rs.getLong("quantity"),
                        rs.getLong("total")), day);
        return new DashboardResponse(date, summary(day), averagePreparation,
                Arrays.stream(byHour).boxed().toList(), top.stream().limit(TOP_PRODUCTS).toList(),
                groups("o.source", day));
    }

    private RevenueSummaryResponse summary(Object[] period) {
        return jdbc.queryForObject("""
                select count(case when o.status <> 'CANCELLED' then 1 end) as orders,
                       coalesce(sum(case when o.status <> 'CANCELLED' then o.total_cents end), 0) as gross,
                       coalesce(sum(case when o.status <> 'CANCELLED' then o.subtotal_cents end), 0) as subtotal,
                       coalesce(sum(case when o.status <> 'CANCELLED' then o.discount_cents end), 0) as discount,
                       coalesce(sum(case when o.status <> 'CANCELLED' then o.platform_subsidy_cents end), 0)
                           as subsidy,
                       coalesce(sum(case when o.status <> 'CANCELLED' then o.delivery_fee_cents end), 0) as delivery,
                       coalesce(sum(case when o.status <> 'CANCELLED' then o.additional_fee_cents end), 0)
                           as additional,
                       count(case when o.status = 'CANCELLED' then 1 end) as cancelled,
                       coalesce(sum(case when o.status = 'CANCELLED' then o.total_cents end), 0) as cancelled_total
                from orders o
                where""" + PERIOD, (rs, row) -> {
            long orders = rs.getLong("orders");
            long gross = rs.getLong("gross");
            return new RevenueSummaryResponse(orders, gross, rs.getLong("subtotal"), rs.getLong("discount"),
                    rs.getLong("subsidy"), rs.getLong("delivery"), rs.getLong("additional"),
                    orders == 0 ? 0 : Math.round((double) gross / orders), rs.getLong("cancelled"),
                    rs.getLong("cancelled_total"));
        }, period);
    }

    /** {@code column} é sempre uma das colunas fixas deste arquivo, nunca vem da requisição. */
    private List<RevenueGroupResponse> groups(String column, Object[] period) {
        return jdbc.query("select " + column + " as grp, count(*) as orders, sum(o.total_cents) as total "
                        + "from orders o where" + PERIOD + "and o.status <> 'CANCELLED' "
                        + "group by " + column + " order by " + column,
                (rs, row) -> new RevenueGroupResponse(rs.getString("grp"), rs.getLong("orders"), rs.getLong("total")),
                period);
    }

    private List<PaymentMethodRevenueResponse> byPaymentMethod(Object[] period) {
        return jdbc.query("select m.id, m.name, m.type, m.sort_order, count(*) as payments, "
                        + "sum(p.amount_cents) as total, "
                        + "coalesce(sum(case when p.status = 'PENDING' then p.amount_cents end), 0) as pending "
                        + "from payment p join orders o on o.id = p.order_id "
                        + "join payment_method m on m.id = p.payment_method_id "
                        + "where" + PERIOD + "and o.status <> 'CANCELLED' and p.status <> 'CANCELLED' "
                        + "group by m.id, m.name, m.type, m.sort_order order by m.sort_order, m.name",
                (rs, row) -> new PaymentMethodRevenueResponse(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("type"), rs.getLong("payments"), rs.getLong("total"), rs.getLong("pending")),
                period);
    }
}
