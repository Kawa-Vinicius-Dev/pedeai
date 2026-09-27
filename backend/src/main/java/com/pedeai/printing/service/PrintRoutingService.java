package com.pedeai.printing.service;

import com.pedeai.catalog.service.SectorService;
import com.pedeai.order.domain.ItemStatus;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.dto.OrderItemResponse;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.event.OrderCreated;
import com.pedeai.order.event.OrderStatusChanged;
import com.pedeai.order.service.OrderService;
import com.pedeai.printing.domain.DocumentType;
import com.pedeai.printing.domain.PrintJob;
import com.pedeai.printing.domain.Printer;
import com.pedeai.printing.domain.PrinterStatus;
import com.pedeai.printing.domain.SectorPrinter;
import com.pedeai.printing.dto.TicketResponse;
import com.pedeai.printing.repository.PrintJobRepository;
import com.pedeai.printing.repository.PrinterRepository;
import com.pedeai.printing.repository.SectorPrinterRepository;
import com.pedeai.store.dto.StoreResponse;
import com.pedeai.store.service.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Impressão automática (docs/04-impressao.md#roteamento): quando o pedido é confirmado, cria um trabalho de produção
 * por setor, na mesma transação. Se o pedido foi confirmado, a impressão existe.
 */
@Service
public class PrintRoutingService {
    /** Ticket de produção que não saiu em 20 min não sai mais sozinho: o pedido já andou. */
    static final Duration PRODUCTION_MAX_AGE = Duration.ofMinutes(20);
    /** O aviso de cancelamento vale mais tempo: comida preparada à toa custa mais que um papel atrasado. */
    static final Duration CANCELLATION_MAX_AGE = Duration.ofMinutes(60);
    /** Impresso, ou pode ter saído papel: nesses casos o setor precisa saber do cancelamento. */
    private static final Set<PrintJob.Status> PAPER_MAY_HAVE_LEFT =
            java.util.EnumSet.of(PrintJob.Status.PRINTED, PrintJob.Status.SENT, PrintJob.Status.UNCERTAIN);
    private static final Logger log = LoggerFactory.getLogger(PrintRoutingService.class);

    private final OrderService orderService;
    private final StoreService storeService;
    private final SectorService sectorService;
    private final AgentService agentService;
    private final PrinterRepository printerRepository;
    private final SectorPrinterRepository sectorPrinterRepository;
    private final PrintJobRepository jobRepository;
    private final TicketService ticketService;
    private final Clock clock;

    public PrintRoutingService(OrderService orderService, StoreService storeService, SectorService sectorService,
                               AgentService agentService, PrinterRepository printerRepository,
                               SectorPrinterRepository sectorPrinterRepository, PrintJobRepository jobRepository,
                               TicketService ticketService, Clock clock) {
        this.orderService = orderService;
        this.storeService = storeService;
        this.sectorService = sectorService;
        this.agentService = agentService;
        this.printerRepository = printerRepository;
        this.sectorPrinterRepository = sectorPrinterRepository;
        this.jobRepository = jobRepository;
        this.ticketService = ticketService;
        this.clock = clock;
    }

    /** Pedido lançado pela equipe já nasce confirmado. O de marketplace nasce recebido e espera o aceite. */
    @EventListener
    public void onOrderCreated(OrderCreated event) {
        if (event.status() != OrderStatus.RECEIVED && event.status() != OrderStatus.CANCELLED) {
            routeProduction(event.storeId(), event.orderId());
        }
    }

    /** Saiu de "recebido" sem ser cancelado: foi aceito (ou pulou direto para o preparo). Cancelado: nada pendente sai. */
    @EventListener
    public void onStatusChanged(OrderStatusChanged event) {
        if (event.to() == OrderStatus.CANCELLED) {
            jobRepository.findAllByOrderIdAndStatus(event.orderId(), PrintJob.Status.PENDING)
                    .forEach(PrintJob::cancelIfPending);
            routeCancellation(event.storeId(), event.orderId());
        } else if (event.from() == OrderStatus.RECEIVED) {
            routeProduction(event.storeId(), event.orderId());
        }
    }

    private void routeProduction(UUID storeId, UUID orderId) {
        OrderResponse order = orderService.get(storeId, orderId);
        StoreResponse store = storeService.get(storeId);
        ZoneId zone = ZoneId.of(store.timezone());
        Instant now = Instant.now(clock);
        Set<UUID> onlineAgents = Set.copyOf(agentService.onlineAgentIds(storeId));
        Map<UUID, List<OrderItemResponse>> bySector = order.items().stream()
                .filter(item -> item.status() == ItemStatus.ACTIVE && item.sectorId() != null)
                .collect(Collectors.groupingBy(OrderItemResponse::sectorId, LinkedHashMap::new, Collectors.toList()));
        bySector.forEach((sectorId, items) -> {
            String key = "order:" + orderId + ":PRODUCTION:" + sectorId + ":ON_CONFIRMED";
            if (jobRepository.existsByStoreIdAndIdempotencyKey(storeId, key)) {
                return;
            }
            Optional<SectorPrinter> config = sectorPrinterRepository.findBySectorIdAndStoreId(sectorId, storeId)
                    .filter(SectorPrinter::isEnabled);
            Optional<Printer> printer = config.flatMap(sectorPrinter -> choose(sectorPrinter, onlineAgents));
            if (printer.isEmpty()) {
                // ponytail: só no log por enquanto; o alerta "Setor sem impressora" na tela vem com o painel.
                log.info("Pedido {} sem impressão no setor {}: setor sem impressora ativa.", order.number(), sectorId);
                return;
            }
            String sectorName = sectorService.get(storeId, sectorId).name();
            TicketResponse ticket = TicketLayout.production(order, sectorName, items, printer.get().getColumns(), zone,
                    now);
            byte[] payload = EscPosRenderer.render(ticket, printer.get().getCodepage(), printer.get().getCutMode(),
                    config.get().getCopies());
            jobRepository.save(new PrintJob(storeId, printer.get().getId(), printer.get().getAgentId(),
                    DocumentType.PRODUCTION_TICKET, orderId, sectorId, PrintJob.Reason.AUTO, key,
                    "Pedido " + order.number() + " · " + sectorName, payload,
                    EscPosRenderer.preview(ticket), PRODUCTION_MAX_AGE, now));
        });
    }

    /**
     * Setor que já recebeu o ticket (ou pode ter recebido, se ficou incerto) ganha o aviso "CANCELADO - NÃO
     * PREPARAR", na mesma impressora em que o pedido saiu. Quem ainda não imprimiu não precisa de aviso.
     */
    private void routeCancellation(UUID storeId, UUID orderId) {
        Instant now = Instant.now(clock);
        Set<UUID> onlineAgents = Set.copyOf(agentService.onlineAgentIds(storeId));
        Map<UUID, UUID> printerBySector = new LinkedHashMap<>();
        jobRepository.findAllByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .filter(job -> job.getDocumentType() == DocumentType.PRODUCTION_TICKET && job.getSectorId() != null)
                .filter(job -> PAPER_MAY_HAVE_LEFT.contains(job.getStatus()))
                .forEach(job -> printerBySector.put(job.getSectorId(), job.getPrinterId()));
        printerBySector.forEach((sectorId, printedOn) -> {
            String key = "order:" + orderId + ":CANCELLATION:" + sectorId;
            if (jobRepository.existsByStoreIdAndIdempotencyKey(storeId, key)) {
                return;
            }
            Optional<Printer> printer = printerRepository.findById(printedOn).filter(Printer::isActive)
                    .or(() -> sectorPrinterRepository.findBySectorIdAndStoreId(sectorId, storeId)
                            .flatMap(config -> choose(config, onlineAgents)));
            if (printer.isEmpty()) {
                log.info("Pedido {} cancelado sem aviso no setor {}: sem impressora ativa.", orderId, sectorId);
                return;
            }
            TicketResponse ticket = ticketService.build(storeId, orderId, DocumentType.CANCELLATION_TICKET, sectorId,
                    printer.get().getColumns());
            String sectorName = sectorService.get(storeId, sectorId).name();
            int number = orderService.get(storeId, orderId).number();
            jobRepository.save(new PrintJob(storeId, printer.get().getId(), printer.get().getAgentId(),
                    DocumentType.CANCELLATION_TICKET, orderId, sectorId, PrintJob.Reason.AUTO, key,
                    "Pedido " + number + " · Cancelamento " + sectorName,
                    EscPosRenderer.render(ticket, printer.get().getCodepage(), printer.get().getCutMode(), 1),
                    EscPosRenderer.preview(ticket), CANCELLATION_MAX_AGE, now));
        });
    }

    /** A principal, ou a reserva se a principal está fora do ar e a reserva não. Sem nenhuma boa, fica na principal. */
    private Optional<Printer> choose(SectorPrinter config, Set<UUID> onlineAgents) {
        List<Printer> candidates = Stream.of(config.getPrinterId(), config.getBackupPrinterId())
                .filter(Objects::nonNull)
                .map(printerRepository::findById)
                .flatMap(Optional::stream)
                .filter(Printer::isActive)
                .toList();
        return candidates.stream()
                .filter(printer -> onlineAgents.contains(printer.getAgentId())
                        && printer.getStatus() != PrinterStatus.OFFLINE && printer.getStatus() != PrinterStatus.ERROR)
                .findFirst()
                .or(() -> candidates.stream().findFirst());
    }
}
