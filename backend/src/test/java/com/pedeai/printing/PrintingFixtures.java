package com.pedeai.printing;

import com.pedeai.order.domain.ItemStatus;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.OrderItemResponse;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.printing.domain.Codepage;
import com.pedeai.printing.domain.ConnectionType;
import com.pedeai.printing.domain.CutMode;
import com.pedeai.printing.domain.DocumentType;
import com.pedeai.printing.domain.PrintJob;
import com.pedeai.printing.domain.Printer;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Role;
import com.pedeai.store.dto.StoreResponse;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static com.pedeai.support.TestSecurity.USER_ID;

/** Objetos prontos para os testes de unidade da impressão. */
public final class PrintingFixtures {
    public static final UUID AGENT_ID = UUID.fromString("01a0d567-0000-7000-8000-0000000000a0");
    public static final UUID KITCHEN = UUID.fromString("01a0d567-0000-7000-8000-0000000000a1");
    public static final UUID BAR = UUID.fromString("01a0d567-0000-7000-8000-0000000000a2");
    public static final UUID ORDER_ID = UUID.fromString("01a0d567-0000-7000-8000-0000000000a3");

    private PrintingFixtures() {
    }

    public static CurrentUser user(Role role) {
        return new CurrentUser(USER_ID, STORE_ID, role, "Ana");
    }

    public static StoreResponse store() {
        return new StoreResponse(STORE_ID, "Lanchonete", null, null, "America/Sao_Paulo", LocalTime.of(5, 0), 1000,
                true, false);
    }

    public static Printer printer(String name) {
        Printer printer = new Printer(STORE_ID, NOW);
        printer.configure(AGENT_ID, name, ConnectionType.NETWORK, "10.0.0.5", 9100, null, 80, 48, Codepage.PC860,
                CutMode.PARTIAL, true, NOW);
        return printer;
    }

    /** Um X-Burger na cozinha e duas cervejas no bar. */
    public static OrderResponse order(OrderStatus status) {
        List<OrderItemResponse> items = List.of(
                new OrderItemResponse(UUID.randomUUID(), null, "10", "X-Burger", KITCHEN, 1, 2990, 0, 2990, null,
                        ItemStatus.ACTIVE, List.of()),
                new OrderItemResponse(UUID.randomUUID(), null, "20", "Cerveja", BAR, 2, 1200, 0, 2400, null,
                        ItemStatus.ACTIVE, List.of()));
        return new OrderResponse(ORDER_ID, 42, LocalDate.of(2026, 9, 24), OrderType.TAKEOUT, OrderSource.PEDEAI,
                status, null, "Rita", null, null, null, items, 5390, 0, 0, 0, 0, 5390, NOW, NOW, null, null, null,
                null, null, null, 0, null, null, null);
    }

    public static PrintJob job(Printer printer, PrintJob.Status status) {
        PrintJob job = new PrintJob(STORE_ID, printer.getId(), printer.getAgentId(), DocumentType.PRODUCTION_TICKET,
                ORDER_ID, KITCHEN, PrintJob.Reason.AUTO, "chave-" + UUID.randomUUID(), "Pedido 42 · Cozinha",
                new byte[]{1}, "texto", Duration.ofMinutes(20), NOW);
        if (status == PrintJob.Status.PENDING) {
            return job;
        }
        job.reserve(NOW);
        switch (status) {
            case UNCERTAIN -> job.uncertain("Agente reiniciou", NOW);
            case PRINTED -> job.printed(NOW);
            case SENT -> {
            }
            default -> throw new IllegalArgumentException("Monte " + status + " no próprio teste.");
        }
        return job;
    }
}
