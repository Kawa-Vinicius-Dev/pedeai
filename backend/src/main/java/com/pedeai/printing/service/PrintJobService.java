package com.pedeai.printing.service;

import com.pedeai.catalog.service.SectorService;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.service.OrderService;
import com.pedeai.printing.domain.DocumentType;
import com.pedeai.printing.domain.PrintJob;
import com.pedeai.printing.domain.Printer;
import com.pedeai.printing.domain.PrinterStatus;
import com.pedeai.printing.dto.PrintAlertResponse;
import com.pedeai.printing.dto.PrintJobResponse;
import com.pedeai.printing.dto.ReprintRequest;
import com.pedeai.printing.dto.TicketResponse;
import com.pedeai.printing.repository.PrintJobRepository;
import com.pedeai.printing.repository.PrinterRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** O painel de impressões: o que precisa de atenção, imprimir de novo, reimprimir e os alertas das telas. */
@Service
public class PrintJobService {
    /** Quem pede para imprimir está olhando: dá mais tempo antes de expirar do que a impressão automática. */
    static final Duration MANUAL_MAX_AGE = Duration.ofMinutes(60);
    static final Duration PANEL_WINDOW = Duration.ofHours(24);
    static final Set<PrintJob.Status> NEEDS_ATTENTION =
            EnumSet.of(PrintJob.Status.FAILED, PrintJob.Status.UNCERTAIN, PrintJob.Status.EXPIRED);
    static final int MAX_IDEMPOTENCY_KEY = 100;
    static final String NOT_FOUND = "Impressão não encontrada.";
    static final String PRINTER_NOT_FOUND = "Impressora não encontrada.";
    static final String PRINTER_INACTIVE = "Esta impressora está inativa. Ative na tela de impressão ou escolha outra.";
    static final String CANNOT_RETRY = "Só dá para imprimir de novo o que falhou, ficou incerto ou expirou.";

    private final PrintJobRepository jobRepository;
    private final PrinterRepository printerRepository;
    private final AgentService agentService;
    private final TicketService ticketService;
    private final OrderService orderService;
    private final SectorService sectorService;
    private final Clock clock;

    public PrintJobService(PrintJobRepository jobRepository, PrinterRepository printerRepository,
                           AgentService agentService, TicketService ticketService, OrderService orderService,
                           SectorService sectorService, Clock clock) {
        this.jobRepository = jobRepository;
        this.printerRepository = printerRepository;
        this.agentService = agentService;
        this.ticketService = ticketService;
        this.orderService = orderService;
        this.sectorService = sectorService;
        this.clock = clock;
    }

    /** Impressões das últimas 24 h, da mais nova para a mais antiga. */
    @Transactional(readOnly = true)
    public List<PrintJobResponse> recent(UUID storeId) {
        return jobRepository.findAllByStoreIdAndCreatedAtAfterOrderByCreatedAtDesc(storeId,
                        Instant.now(clock).minus(PANEL_WINDOW)).stream()
                .map(PrintJobResponse::from)
                .toList();
    }

    /** Imprimir de novo o que falhou, ficou incerto ou expirou, na mesma impressora ou em outra. */
    @Transactional
    public PrintJobResponse retry(CurrentUser user, UUID jobId, UUID printerId) {
        PrintJob job = jobRepository.findByIdAndStoreId(jobId, user.storeId())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        TicketService.requireAllowed(user, job.getDocumentType());
        if (!NEEDS_ATTENTION.contains(job.getStatus())) {
            throw new ConflictException(CANNOT_RETRY);
        }
        Printer printer = activePrinter(user.storeId(), printerId == null ? job.getPrinterId() : printerId);
        TicketResponse ticket = ticketService.build(user.storeId(), job.getOrderId(), job.getDocumentType(),
                job.getSectorId(), printer.getColumns());
        job.requeue(printer.getId(), printer.getAgentId(), EscPosRenderer.render(ticket, printer.getCodepage(),
                printer.getCutMode(), 1), EscPosRenderer.preview(ticket), MANUAL_MAX_AGE, Instant.now(clock));
        return PrintJobResponse.from(job);
    }

    /**
     * Reimpressão pedida no detalhe do pedido: sai com a faixa REIMPRESSÃO. {@code idempotencyKey}: um por clique,
     * para o duplo clique não imprimir duas vezes.
     */
    @Transactional
    public PrintJobResponse reprint(CurrentUser user, UUID orderId, ReprintRequest request, String idempotencyKey) {
        TicketService.requireAllowed(user, request.documentType());
        if (idempotencyKey != null && idempotencyKey.length() > MAX_IDEMPOTENCY_KEY) {
            throw new BusinessRuleException("Idempotency-Key pode ter até " + MAX_IDEMPOTENCY_KEY + " caracteres.");
        }
        Printer printer = activePrinter(user.storeId(), request.printerId());
        String key = "reprint:" + orderId + ":" + (idempotencyKey == null ? UUID.randomUUID() : idempotencyKey);
        var existing = jobRepository.findByStoreIdAndIdempotencyKey(user.storeId(), key);
        if (existing.isPresent()) {
            return PrintJobResponse.from(existing.get());
        }
        UUID sectorId = request.documentType() == DocumentType.PRODUCTION_TICKET ? request.sectorId() : null;
        TicketResponse original = ticketService.build(user.storeId(), orderId, request.documentType(), sectorId,
                printer.getColumns());
        OrderResponse order = orderService.get(user.storeId(), orderId);
        TicketResponse ticket = ticketService.markReprint(user.storeId(), original, order.createdAt());
        String document = sectorId == null ? "Via completa" : sectorService.get(user.storeId(), sectorId).name();
        String title = "Pedido " + order.number() + " · " + document + " (reimpressão)";
        PrintJob job = new PrintJob(user.storeId(), printer.getId(), printer.getAgentId(), request.documentType(),
                orderId, sectorId, PrintJob.Reason.REPRINT, key, title,
                EscPosRenderer.render(ticket, printer.getCodepage(), printer.getCutMode(), 1),
                EscPosRenderer.preview(ticket), MANUAL_MAX_AGE, Instant.now(clock));
        return PrintJobResponse.from(jobRepository.save(job));
    }

    /**
     * Alertas para a faixa das telas: impressora fora do ar (ou o computador dela) com quantos pedidos esperam, e
     * impressões que precisam de alguém decidir.
     */
    @Transactional(readOnly = true)
    public List<PrintAlertResponse> alerts(UUID storeId) {
        Set<UUID> onlineAgents = Set.copyOf(agentService.onlineAgentIds(storeId));
        List<PrintAlertResponse> alerts = new ArrayList<>();
        for (Printer printer : printerRepository.findAllByStoreIdOrderByNameAsc(storeId)) {
            if (!printer.isActive()) {
                continue;
            }
            String problem = !onlineAgents.contains(printer.getAgentId()) ? "sem conexão com o computador de impressão"
                    : printer.getStatus() == PrinterStatus.OFFLINE ? "offline"
                    : printer.getStatus() == PrinterStatus.ERROR ? "com problema"
                    + (printer.getStatusDetail() == null ? "" : " (" + printer.getStatusDetail() + ")")
                    : null;
            if (problem == null) {
                continue;
            }
            int waiting = (int) jobRepository.countByPrinterIdAndStatus(printer.getId(), PrintJob.Status.PENDING);
            String suffix = waiting == 0 ? "" : waiting == 1 ? ": 1 impressão aguardando" : ": " + waiting
                    + " impressões aguardando";
            alerts.add(new PrintAlertResponse(printer.getId(), "Impressora " + printer.getName() + " " + problem
                    + suffix + ".", waiting));
        }
        int attention = (int) jobRepository.countByStoreIdAndStatusInAndCreatedAtAfter(storeId, NEEDS_ATTENTION,
                Instant.now(clock).minus(PANEL_WINDOW));
        if (attention > 0) {
            alerts.add(new PrintAlertResponse(null, attention == 1 ? "1 impressão precisa de atenção."
                    : attention + " impressões precisam de atenção.", attention));
        }
        return alerts;
    }

    private Printer activePrinter(UUID storeId, UUID printerId) {
        Printer printer = printerRepository.findByIdAndStoreId(printerId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(PRINTER_NOT_FOUND));
        if (!printer.isActive()) {
            throw new BusinessRuleException(PRINTER_INACTIVE);
        }
        return printer;
    }
}
