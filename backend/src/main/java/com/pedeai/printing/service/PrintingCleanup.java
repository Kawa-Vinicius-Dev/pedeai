package com.pedeai.printing.service;

import com.pedeai.printing.domain.PrintJob;
import com.pedeai.printing.repository.AgentPairingCodeRepository;
import com.pedeai.printing.repository.PrintJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

/**
 * Cada trabalho guarda os bytes do ticket. Encerrado há 30 dias, não serve mais para o painel nem para reimprimir
 * (a reimpressão monta o ticket de novo a partir do pedido). Código de pareamento vencido também sai.
 */
@Service
public class PrintingCleanup {
    static final Duration KEEP_JOBS = Duration.ofDays(30);
    static final Duration KEEP_CODES_AFTER_EXPIRY = Duration.ofDays(1);
    private static final Logger log = LoggerFactory.getLogger(PrintingCleanup.class);

    private final PrintJobRepository jobs;
    private final AgentPairingCodeRepository codes;
    private final Clock clock;

    public PrintingCleanup(PrintJobRepository jobs, AgentPairingCodeRepository codes, Clock clock) {
        this.jobs = jobs;
        this.codes = codes;
        this.clock = clock;
    }

    @Scheduled(cron = "0 35 4 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public int cleanUp() {
        Instant now = Instant.now(clock);
        int deletedJobs = jobs.deleteClosedBefore(now.minus(KEEP_JOBS),
                EnumSet.of(PrintJob.Status.PENDING, PrintJob.Status.SENT));
        int deletedCodes = codes.deleteExpiredBefore(now.minus(KEEP_CODES_AFTER_EXPIRY));
        log.info("Limpeza: {} trabalhos de impressão e {} códigos de pareamento apagados.", deletedJobs, deletedCodes);
        return deletedJobs + deletedCodes;
    }
}
