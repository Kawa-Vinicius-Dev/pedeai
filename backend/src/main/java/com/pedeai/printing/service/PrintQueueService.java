package com.pedeai.printing.service;

import com.pedeai.order.service.OrderService;
import com.pedeai.printing.domain.PrintJob;
import com.pedeai.printing.dto.AgentJobResponse;
import com.pedeai.printing.dto.AgentJobUpdateRequest;
import com.pedeai.printing.dto.AgentPrincipal;
import com.pedeai.printing.dto.PrintJobResponse;
import com.pedeai.printing.repository.PrintJobRepository;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A fila vista pelo agente, e a manutenção periódica dela (reserva vencida e trabalho velho demais). */
@Service
public class PrintQueueService {
    static final String NOT_FOUND = "Trabalho de impressão não encontrado.";
    static final String ALREADY_TAKEN = "Este trabalho já foi reservado ou não está mais na fila.";

    private final PrintJobRepository repository;
    private final OrderService orderService;
    private final Clock clock;

    public PrintQueueService(PrintJobRepository repository, OrderService orderService, Clock clock) {
        this.repository = repository;
        this.orderService = orderService;
        this.clock = clock;
    }

    /** Pendentes das impressoras deste agente, prontos para tentar agora, do mais antigo para o mais novo. */
    @Transactional(readOnly = true)
    public List<AgentJobResponse> pendingFor(AgentPrincipal agent) {
        return repository.findAllByAgentIdAndStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        agent.agentId(), PrintJob.Status.PENDING, Instant.now(clock)).stream()
                .map(AgentJobResponse::from)
                .toList();
    }

    /** Impressões de um pedido, para o detalhe do pedido. */
    @Transactional(readOnly = true)
    public List<PrintJobResponse> forOrder(UUID storeId, UUID orderId) {
        orderService.get(storeId, orderId);
        return repository.findAllByOrderIdOrderByCreatedAtAsc(orderId).stream().map(PrintJobResponse::from).toList();
    }

    @Transactional
    public void update(AgentPrincipal agent, UUID jobId, AgentJobUpdateRequest request) {
        PrintJob job = repository.findByIdAndAgentId(jobId, agent.agentId())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        Instant now = Instant.now(clock);
        switch (request.status()) {
            case SENT -> {
                if (!job.reserve(now)) {
                    throw new ConflictException(ALREADY_TAKEN);
                }
            }
            // A confirmação pode chegar de novo se a resposta anterior se perdeu na rede.
            case PRINTED -> {
                if (job.getStatus() != PrintJob.Status.PRINTED) {
                    requireSent(job);
                    job.printed(now);
                }
            }
            case FAILED -> {
                requireSent(job);
                job.failed(request.error(), now);
            }
            case UNCERTAIN -> {
                requireSent(job);
                job.uncertain(request.error(), now);
            }
        }
    }

    /** A cada 15 s: reserva vencida volta para a fila, e o pendente velho demais expira. */
    @Scheduled(fixedDelay = 15_000)
    @Transactional
    public void maintain() {
        Instant now = Instant.now(clock);
        repository.findAllByStatusAndLeaseUntilBefore(PrintJob.Status.SENT, now)
                .forEach(job -> job.releaseExpiredLease(now));
        repository.findAllByStatusAndExpiresAtBefore(PrintJob.Status.PENDING, now)
                .forEach(job -> job.expireIfStale(now));
    }

    private static void requireSent(PrintJob job) {
        if (job.getStatus() != PrintJob.Status.SENT) {
            throw new ConflictException("O trabalho não está reservado por este computador (" + job.getStatus() + ").");
        }
    }
}
