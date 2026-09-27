package com.pedeai.printing.service;

import com.pedeai.catalog.dto.SectorResponse;
import com.pedeai.catalog.service.SectorService;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.event.OrderCreated;
import com.pedeai.order.event.OrderStatusChanged;
import com.pedeai.order.service.OrderService;
import com.pedeai.printing.domain.PrintJob;
import com.pedeai.printing.domain.Printer;
import com.pedeai.printing.domain.PrinterStatus;
import com.pedeai.printing.domain.SectorPrinter;
import com.pedeai.printing.repository.PrintJobRepository;
import com.pedeai.printing.repository.PrinterRepository;
import com.pedeai.printing.repository.SectorPrinterRepository;
import com.pedeai.store.service.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static com.pedeai.printing.PrintingFixtures.AGENT_ID;
import static com.pedeai.printing.PrintingFixtures.BAR;
import static com.pedeai.printing.PrintingFixtures.KITCHEN;
import static com.pedeai.printing.PrintingFixtures.ORDER_ID;
import static com.pedeai.printing.PrintingFixtures.job;
import static com.pedeai.printing.PrintingFixtures.order;
import static com.pedeai.printing.PrintingFixtures.printer;
import static com.pedeai.printing.PrintingFixtures.store;
import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static com.pedeai.support.TestSecurity.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrintRoutingServiceTest {
    @Mock
    private OrderService orders;
    @Mock
    private StoreService stores;
    @Mock
    private SectorService sectors;
    @Mock
    private AgentService agents;
    @Mock
    private PrinterRepository printers;
    @Mock
    private SectorPrinterRepository sectorPrinters;
    @Mock
    private PrintJobRepository jobs;
    @Mock
    private TicketService tickets;

    private PrintRoutingService service;
    private Printer kitchenPrinter;
    private Printer backupPrinter;
    private SectorPrinter kitchenConfig;

    @BeforeEach
    void setUp() {
        service = new PrintRoutingService(orders, stores, sectors, agents, printers, sectorPrinters, jobs, tickets, CLOCK);
        kitchenPrinter = printer("Cozinha");
        backupPrinter = printer("Caixa");
        kitchenConfig = new SectorPrinter(KITCHEN, STORE_ID);
        kitchenConfig.configure(kitchenPrinter.getId(), backupPrinter.getId(), 1, true);
        when(orders.get(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.CONFIRMED));
        when(stores.get(STORE_ID)).thenReturn(store());
        when(sectors.get(STORE_ID, KITCHEN)).thenReturn(new SectorResponse(KITCHEN, "Cozinha", true, true));
        when(agents.onlineAgentIds(STORE_ID)).thenReturn(List.of(AGENT_ID));
        when(printers.findById(kitchenPrinter.getId())).thenReturn(Optional.of(kitchenPrinter));
        when(printers.findById(backupPrinter.getId())).thenReturn(Optional.of(backupPrinter));
        when(sectorPrinters.findBySectorIdAndStoreId(KITCHEN, STORE_ID)).thenReturn(Optional.of(kitchenConfig));
        when(sectorPrinters.findBySectorIdAndStoreId(BAR, STORE_ID)).thenReturn(Optional.empty());
    }

    private static OrderCreated created(OrderStatus status) {
        return new OrderCreated(STORE_ID, ORDER_ID, 42, status, 0, 5390, List.of(), USER_ID,
                com.pedeai.order.domain.OrderSource.PEDEAI);
    }

    @Test
    void confirmedOrderPrintsOnlyTheSectorsThatHaveAPrinter() {
        service.onOrderCreated(created(OrderStatus.CONFIRMED));

        ArgumentCaptor<PrintJob> saved = ArgumentCaptor.forClass(PrintJob.class);
        verify(jobs).save(saved.capture());
        assertThat(saved.getValue().getPrinterId()).isEqualTo(kitchenPrinter.getId());
        assertThat(saved.getValue().getTitle()).isEqualTo("Pedido 42 · Cozinha");
        assertThat(saved.getValue().getPreview()).contains("1x X-BURGER").doesNotContain("CERVEJA");
        assertThat(saved.getValue().getPayload()).startsWith(0x1B, '@', 0x1B, 't', 3);
    }

    @Test
    void orderWaitingForAcceptanceDoesNotPrintUntilItLeavesReceived() {
        service.onOrderCreated(created(OrderStatus.RECEIVED));
        verify(jobs, never()).save(any());

        service.onStatusChanged(new OrderStatusChanged(STORE_ID, ORDER_ID, 42, OrderStatus.RECEIVED,
                OrderStatus.CONFIRMED, 1, com.pedeai.order.domain.ActorType.USER));
        verify(jobs).save(any());
    }

    @Test
    void theSameEventTwiceDoesNotPrintTwice() {
        when(jobs.existsByStoreIdAndIdempotencyKey(eq(STORE_ID), anyString())).thenReturn(true);

        service.onOrderCreated(created(OrderStatus.CONFIRMED));

        verify(jobs, never()).save(any());
    }

    @Test
    void mainPrinterDownSendsToTheBackup() {
        kitchenPrinter.reportStatus(PrinterStatus.OFFLINE, "Sem resposta", NOW);

        service.onOrderCreated(created(OrderStatus.CONFIRMED));

        ArgumentCaptor<PrintJob> saved = ArgumentCaptor.forClass(PrintJob.class);
        verify(jobs).save(saved.capture());
        assertThat(saved.getValue().getPrinterId()).isEqualTo(backupPrinter.getId());
    }

    @Test
    void disabledSectorDoesNotPrint() {
        kitchenConfig.configure(kitchenPrinter.getId(), null, 1, false);

        service.onOrderCreated(created(OrderStatus.CONFIRMED));

        verify(jobs, never()).save(any());
    }

    @Test
    void sectorThatAlreadyPrintedGetsTheCancellationNoticeOnTheSamePrinter() {
        PrintJob printedInKitchen = job(kitchenPrinter, PrintJob.Status.PRINTED);
        PrintJob waitingInBar = new PrintJob(STORE_ID, backupPrinter.getId(), AGENT_ID,
                com.pedeai.printing.domain.DocumentType.PRODUCTION_TICKET, ORDER_ID, BAR, PrintJob.Reason.AUTO,
                "bar", "Pedido 42 · Bar", new byte[]{1}, "texto", java.time.Duration.ofMinutes(20), NOW);
        when(jobs.findAllByOrderIdAndStatus(ORDER_ID, PrintJob.Status.PENDING)).thenReturn(List.of(waitingInBar));
        when(jobs.findAllByOrderIdOrderByCreatedAtAsc(ORDER_ID)).thenReturn(List.of(printedInKitchen, waitingInBar));
        when(tickets.build(eq(STORE_ID), eq(ORDER_ID), eq(com.pedeai.printing.domain.DocumentType.CANCELLATION_TICKET),
                eq(KITCHEN), eq(48))).thenReturn(new com.pedeai.printing.dto.TicketResponse(
                com.pedeai.printing.domain.DocumentType.CANCELLATION_TICKET, 48, List.of(
                new com.pedeai.printing.dto.TicketLineResponse("CANCELADO",
                        com.pedeai.printing.dto.TicketLineResponse.Align.CENTER, false, true))));

        service.onStatusChanged(new OrderStatusChanged(STORE_ID, ORDER_ID, 42, OrderStatus.CONFIRMED,
                OrderStatus.CANCELLED, 2, com.pedeai.order.domain.ActorType.USER));

        assertThat(waitingInBar.getStatus()).isEqualTo(PrintJob.Status.CANCELLED);
        ArgumentCaptor<PrintJob> saved = ArgumentCaptor.forClass(PrintJob.class);
        verify(jobs).save(saved.capture());
        assertThat(saved.getValue().getDocumentType())
                .isEqualTo(com.pedeai.printing.domain.DocumentType.CANCELLATION_TICKET);
        assertThat(saved.getValue().getPrinterId()).isEqualTo(kitchenPrinter.getId());
        assertThat(saved.getValue().getTitle()).isEqualTo("Pedido 42 · Cancelamento Cozinha");
    }

    @Test
    void cancellingTheOrderCancelsWhatIsStillWaiting() {
        PrintJob pending = job(kitchenPrinter, PrintJob.Status.PENDING);
        when(jobs.findAllByOrderIdAndStatus(ORDER_ID, PrintJob.Status.PENDING)).thenReturn(List.of(pending));

        service.onStatusChanged(new OrderStatusChanged(STORE_ID, ORDER_ID, 42, OrderStatus.CONFIRMED,
                OrderStatus.CANCELLED, 2, com.pedeai.order.domain.ActorType.USER));

        assertThat(pending.getStatus()).isEqualTo(PrintJob.Status.CANCELLED);
    }
}
