package com.pedeai.printing.service;

import com.pedeai.catalog.dto.SectorResponse;
import com.pedeai.catalog.service.SectorService;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.service.OrderService;
import com.pedeai.payment.domain.CashSession;
import com.pedeai.payment.domain.PaymentMethodType;
import com.pedeai.payment.dto.CashLineResponse;
import com.pedeai.payment.dto.CashSessionResponse;
import com.pedeai.payment.service.CashSessionService;
import com.pedeai.printing.domain.DocumentType;
import com.pedeai.printing.domain.PrintJob;
import com.pedeai.printing.domain.Printer;
import com.pedeai.printing.domain.PrinterStatus;
import com.pedeai.printing.dto.PrintAlertResponse;
import com.pedeai.printing.dto.PrintJobResponse;
import com.pedeai.printing.dto.ReprintRequest;
import com.pedeai.printing.dto.TicketLineResponse;
import com.pedeai.printing.dto.TicketResponse;
import com.pedeai.printing.repository.PrintJobRepository;
import com.pedeai.printing.repository.PrinterRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ForbiddenOperationException;
import com.pedeai.shared.security.Role;
import com.pedeai.store.service.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pedeai.printing.PrintingFixtures.AGENT_ID;
import static com.pedeai.printing.PrintingFixtures.KITCHEN;
import static com.pedeai.printing.PrintingFixtures.ORDER_ID;
import static com.pedeai.printing.PrintingFixtures.job;
import static com.pedeai.printing.PrintingFixtures.order;
import static com.pedeai.printing.PrintingFixtures.printer;
import static com.pedeai.printing.PrintingFixtures.store;
import static com.pedeai.printing.PrintingFixtures.user;
import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrintJobServiceTest {
    private static final TicketResponse TICKET = new TicketResponse(DocumentType.PRODUCTION_TICKET, 48,
            List.of(new TicketLineResponse("COZINHA - PEDIDO 42", TicketLineResponse.Align.CENTER, false, true)));

    @Mock
    private PrintJobRepository jobs;
    @Mock
    private PrinterRepository printers;
    @Mock
    private AgentService agents;
    @Mock
    private TicketService tickets;
    @Mock
    private OrderService orders;
    @Mock
    private SectorService sectors;
    @Mock
    private CashSessionService cashSessions;
    @Mock
    private StoreService stores;

    private PrintJobService service;
    private Printer kitchen;
    private Printer cashier;

    @BeforeEach
    void setUp() {
        service = new PrintJobService(jobs, printers, agents, tickets, orders, sectors, cashSessions, stores,
                CLOCK);
        kitchen = printer("Cozinha");
        cashier = printer("Caixa");
        when(printers.findByIdAndStoreId(kitchen.getId(), STORE_ID)).thenReturn(Optional.of(kitchen));
        when(printers.findByIdAndStoreId(cashier.getId(), STORE_ID)).thenReturn(Optional.of(cashier));
        when(tickets.build(eq(STORE_ID), eq(ORDER_ID), any(), any(), anyInt())).thenReturn(TICKET);
        when(tickets.markReprint(eq(STORE_ID), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(orders.get(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.CONFIRMED));
        when(sectors.get(STORE_ID, KITCHEN)).thenReturn(new SectorResponse(KITCHEN, "Cozinha", true, true));
        when(jobs.save(any())).then(returnsFirstArg());
        when(agents.onlineAgentIds(STORE_ID)).thenReturn(List.of(AGENT_ID));
        when(stores.get(STORE_ID)).thenReturn(store());
    }

    @Test
    void closedCashReportPrintsTheCountAndItsRetryKeepsTheSameBytes() {
        UUID sessionId = UUID.randomUUID();
        UUID cash = UUID.randomUUID();
        when(cashSessions.get(STORE_ID, sessionId)).thenReturn(new CashSessionResponse(sessionId,
                CashSession.Status.CLOSED, NOW.minusSeconds(36_000), "Ana", 10_000, NOW, "Ana", null,
                List.of(new CashLineResponse(cash, "Dinheiro", PaymentMethodType.CASH, 5_000, 2, 15_000, 14_500L,
                        -500L)),
                List.of(), 15_000, 14_500L, -500L));
        when(jobs.findByStoreIdAndIdempotencyKey(any(), anyString())).thenReturn(Optional.empty());

        PrintJobResponse printed = service.printCashReport(user(Role.CASHIER), sessionId, cashier.getId(), "clique");

        assertThat(printed.title()).isEqualTo("Fechamento de caixa");
        assertThat(printed.orderId()).isNull();
        verify(jobs).save(org.mockito.ArgumentMatchers.argThat(job -> job.getPreview().contains("FECHAMENTO DE CAIXA")
                && job.getPreview().contains("-5,00")));

        PrintJob report = new PrintJob(STORE_ID, cashier.getId(), AGENT_ID, DocumentType.CASH_REPORT, null, null,
                PrintJob.Reason.MANUAL, "cash:x", "Fechamento de caixa", new byte[]{1, 2, 3}, "texto",
                java.time.Duration.ofMinutes(5), NOW);
        report.reserve(NOW);
        report.uncertain("Agente reiniciou", NOW);
        when(jobs.findByIdAndStoreId(report.getId(), STORE_ID)).thenReturn(Optional.of(report));
        service.retry(user(Role.CASHIER), report.getId(), kitchen.getId());
        assertThat(report.getPayload()).containsExactly(1, 2, 3);
        assertThat(report.getPrinterId()).isEqualTo(kitchen.getId());
        verify(tickets, never()).build(any(), any(), eq(DocumentType.CASH_REPORT), any(), anyInt());
    }

    @Test
    void uncertainJobGoesToAnotherPrinterWithANewDeliveryKey() {
        PrintJob uncertain = job(kitchen, PrintJob.Status.UNCERTAIN);
        String oldKey = uncertain.getDeliveryKey();
        when(jobs.findByIdAndStoreId(uncertain.getId(), STORE_ID)).thenReturn(Optional.of(uncertain));

        PrintJobResponse retried = service.retry(user(Role.CASHIER), uncertain.getId(), cashier.getId());

        assertThat(retried.status()).isEqualTo(PrintJob.Status.PENDING);
        assertThat(retried.printerId()).isEqualTo(cashier.getId());
        assertThat(uncertain.getDeliveryKey()).isNotEqualTo(oldKey);
    }

    @Test
    void printedJobIsNotRetriedAndAnInactivePrinterIsRefused() {
        PrintJob printed = job(kitchen, PrintJob.Status.PRINTED);
        when(jobs.findByIdAndStoreId(printed.getId(), STORE_ID)).thenReturn(Optional.of(printed));
        assertThatThrownBy(() -> service.retry(user(Role.CASHIER), printed.getId(), null))
                .isInstanceOf(ConflictException.class);

        PrintJob uncertain = job(kitchen, PrintJob.Status.UNCERTAIN);
        when(jobs.findByIdAndStoreId(uncertain.getId(), STORE_ID)).thenReturn(Optional.of(uncertain));
        cashier.configure(cashier.getAgentId(), "Caixa", cashier.getConnectionType(), "10.0.0.5", 9100, null, 80, 48,
                cashier.getCodepage(), cashier.getCutMode(), false, NOW);
        assertThatThrownBy(() -> service.retry(user(Role.CASHIER), uncertain.getId(), cashier.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(PrintJobService.PRINTER_INACTIVE);
    }

    @Test
    void reprintIsMarkedAndTheSameClickReturnsTheSameJob() {
        ReprintRequest request = new ReprintRequest(DocumentType.PRODUCTION_TICKET, KITCHEN, kitchen.getId());
        when(jobs.findByStoreIdAndIdempotencyKey(STORE_ID, "reprint:" + ORDER_ID + ":clique-1"))
                .thenReturn(Optional.empty());

        PrintJobResponse first = service.reprint(user(Role.KITCHEN), ORDER_ID, request, "clique-1");

        assertThat(first.reason()).isEqualTo(PrintJob.Reason.REPRINT);
        assertThat(first.title()).isEqualTo("Pedido 42 · Cozinha (reimpressão)");
        verify(tickets).markReprint(eq(STORE_ID), eq(TICKET), eq(NOW));

        PrintJob existing = job(kitchen, PrintJob.Status.PENDING);
        when(jobs.findByStoreIdAndIdempotencyKey(STORE_ID, "reprint:" + ORDER_ID + ":clique-1"))
                .thenReturn(Optional.of(existing));
        assertThat(service.reprint(user(Role.KITCHEN), ORDER_ID, request, "clique-1").id()).isEqualTo(existing.getId());
    }

    @Test
    void cancellationNoticeIsReprintedForItsSector() {
        when(jobs.findByStoreIdAndIdempotencyKey(any(), anyString())).thenReturn(Optional.empty());

        PrintJobResponse reprinted = service.reprint(user(Role.KITCHEN), ORDER_ID,
                new ReprintRequest(DocumentType.CANCELLATION_TICKET, KITCHEN, kitchen.getId()), null);

        verify(tickets).build(STORE_ID, ORDER_ID, DocumentType.CANCELLATION_TICKET, KITCHEN, 48);
        assertThat(reprinted.sectorId()).isEqualTo(KITCHEN);
    }

    @Test
    void kitchenDoesNotReprintTheFullTicketAndHugeKeysAreRefused() {
        assertThatThrownBy(() -> service.reprint(user(Role.KITCHEN), ORDER_ID,
                new ReprintRequest(DocumentType.ORDER_TICKET, null, cashier.getId()), null))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.reprint(user(Role.CASHIER), ORDER_ID,
                new ReprintRequest(DocumentType.ORDER_TICKET, null, cashier.getId()), "x".repeat(101)))
                .isInstanceOf(BusinessRuleException.class);
        verify(jobs, never()).save(any());
    }

    @Test
    void alertsNameTheProblemAndTheWaitingJobs() {
        kitchen.reportStatus(PrinterStatus.ERROR, "Sem papel", NOW);
        when(printers.findAllByStoreIdOrderByNameAsc(STORE_ID)).thenReturn(List.of(cashier, kitchen));
        when(jobs.countByPrinterIdAndStatus(kitchen.getId(), PrintJob.Status.PENDING)).thenReturn(3L);
        when(jobs.countByStoreIdAndStatusInAndCreatedAtAfter(eq(STORE_ID), any(), any())).thenReturn(1L);

        List<PrintAlertResponse> alerts = service.alerts(STORE_ID);

        assertThat(alerts).extracting(PrintAlertResponse::message).containsExactly(
                "Impressora Cozinha com problema (Sem papel): 3 impressões aguardando.",
                "1 impressão precisa de atenção.");
    }

    @Test
    void agentOfflineIsAnAlertForItsPrinters() {
        when(agents.onlineAgentIds(STORE_ID)).thenReturn(List.of());
        when(printers.findAllByStoreIdOrderByNameAsc(STORE_ID)).thenReturn(List.of(kitchen));
        when(jobs.countByPrinterIdAndStatus(kitchen.getId(), PrintJob.Status.PENDING)).thenReturn(0L);

        assertThat(service.alerts(STORE_ID)).extracting(PrintAlertResponse::message)
                .containsExactly("Impressora Cozinha sem conexão com o computador de impressão.");
        verify(jobs, never()).findByStoreIdAndIdempotencyKey(any(), anyString());
    }
}
