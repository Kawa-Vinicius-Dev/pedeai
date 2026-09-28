package com.pedeai.printing.service;

import com.pedeai.printing.domain.AgentPairingCode;
import com.pedeai.printing.domain.PrintAgent;
import com.pedeai.printing.domain.Printer;
import com.pedeai.printing.dto.AgentConfigResponse;
import com.pedeai.printing.dto.AgentConfigResponse.AgentPrinterResponse;
import com.pedeai.printing.dto.AgentPairingRequest;
import com.pedeai.printing.dto.AgentPairingResponse;
import com.pedeai.printing.dto.AgentPrincipal;
import com.pedeai.printing.dto.AgentStatusRequest;
import com.pedeai.printing.dto.PairingCodeResponse;
import com.pedeai.printing.dto.PrintAgentResponse;
import com.pedeai.printing.repository.AgentPairingCodeRepository;
import com.pedeai.printing.repository.PrintAgentRepository;
import com.pedeai.printing.repository.PrinterRepository;
import com.pedeai.shared.exception.TooManyRequestsException;
import com.pedeai.shared.security.AttemptLimiter;
import com.pedeai.shared.exception.InvalidCredentialsException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.security.SecretTokens;
import com.pedeai.store.service.StoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pareamento, autenticação e heartbeat dos agentes de impressão (docs/04-impressao.md#o-agente). */
@Service
public class AgentService {
    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final String INVALID_CODE = "Código inválido ou vencido. Gere outro na tela de impressão.";
    static final String TOO_MANY_ATTEMPTS = "Muitas tentativas com código errado. Espere alguns minutos.";
    static final String NOT_FOUND = "Computador de impressão não encontrado.";
    private static final int CODE_DIGITS = 6;
    /** O código tem só 6 dígitos: sem limite, dava para tentar todos. */
    static final int MAX_FAILED_PAIRINGS = 10;
    static final Duration FAILED_PAIRING_WINDOW = Duration.ofMinutes(10);
    /**
     * Teto somando todos os IPs: o IP vem de um cabeçalho que dá para forjar. Com ele, testar o milhão de códigos
     * possíveis levaria décadas, qualquer que seja o IP informado.
     */
    static final int MAX_FAILED_PAIRINGS_GLOBAL = 200;
    private static final String GLOBAL = "*";

    private final PrintAgentRepository agentRepository;
    private final AgentPairingCodeRepository codeRepository;
    private final PrinterRepository printerRepository;
    private final StoreService storeService;
    private final Clock clock;
    private final AttemptLimiter failedPairings;
    private final AttemptLimiter allFailedPairings;

    public AgentService(PrintAgentRepository agentRepository, AgentPairingCodeRepository codeRepository,
                        PrinterRepository printerRepository, StoreService storeService, Clock clock) {
        this.agentRepository = agentRepository;
        this.codeRepository = codeRepository;
        this.printerRepository = printerRepository;
        this.storeService = storeService;
        this.clock = clock;
        this.failedPairings = new AttemptLimiter(MAX_FAILED_PAIRINGS, FAILED_PAIRING_WINDOW, clock);
        this.allFailedPairings = new AttemptLimiter(MAX_FAILED_PAIRINGS_GLOBAL, FAILED_PAIRING_WINDOW, clock);
    }

    @Transactional
    public PairingCodeResponse createPairingCode(UUID storeId) {
        Instant now = Instant.now(clock);
        String code;
        do {
            code = SecretTokens.newDigits(CODE_DIGITS);
        } while (usableCode(SecretTokens.sha256(code), now).isPresent());
        AgentPairingCode pairingCode = new AgentPairingCode(storeId, SecretTokens.sha256(code), now.plus(CODE_TTL),
                now);
        codeRepository.save(pairingCode);
        return new PairingCodeResponse(code, now.plus(CODE_TTL));
    }

    /** Troca o código pelo token de dispositivo. {@code clientKey}: de onde veio a tentativa (o IP). */
    @Transactional
    public AgentPairingResponse pair(AgentPairingRequest request, String clientKey) {
        Instant now = Instant.now(clock);
        if (failedPairings.blocked(clientKey) || allFailedPairings.blocked(GLOBAL)) {
            throw new TooManyRequestsException(TOO_MANY_ATTEMPTS);
        }
        Optional<AgentPairingCode> found = usableCode(SecretTokens.sha256(request.code()), now);
        if (found.isEmpty()) {
            failedPairings.failed(clientKey);
            allFailedPairings.failed(GLOBAL);
            throw new InvalidCredentialsException(INVALID_CODE);
        }
        AgentPairingCode code = found.get();
        code.use(now);
        String token = SecretTokens.newValue();
        PrintAgent agent = agentRepository.save(new PrintAgent(code.getStoreId(), request.name().trim(),
                SecretTokens.sha256(token), request.os(), request.agentVersion(), now));
        String storeName = storeService.get(code.getStoreId()).name();
        return new AgentPairingResponse(agent.getId(), agent.getName(), storeName, token);
    }

    /** Usado a cada chamada do agente. Token revogado não autentica. */
    @Transactional(readOnly = true)
    public Optional<AgentPrincipal> authenticate(String token) {
        return agentRepository.findByTokenHashAndRevokedAtIsNull(SecretTokens.sha256(token))
                .map(agent -> new AgentPrincipal(agent.getId(), agent.getStoreId()));
    }

    @Transactional(readOnly = true)
    public List<PrintAgentResponse> list(UUID storeId) {
        Instant now = Instant.now(clock);
        return agentRepository.findAllByStoreIdAndRevokedAtIsNullOrderByCreatedAtAsc(storeId).stream()
                .map(agent -> PrintAgentResponse.from(agent, now))
                .toList();
    }

    /** O token para de valer na hora. As impressoras continuam cadastradas para trocar de computador. */
    @Transactional
    public void revoke(UUID storeId, UUID id) {
        find(storeId, id).revoke(Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public AgentConfigResponse config(AgentPrincipal principal) {
        PrintAgent agent = find(principal.storeId(), principal.agentId());
        return new AgentConfigResponse(agent.getId(), agent.getName(),
                printerRepository.findAllByAgentIdAndActiveTrueOrderByNameAsc(agent.getId()).stream()
                        .map(AgentPrinterResponse::from)
                        .toList());
    }

    /** Heartbeat. Status de impressora que não é deste agente é ignorado. */
    @Transactional
    public void heartbeat(AgentPrincipal principal, AgentStatusRequest request) {
        Instant now = Instant.now(clock);
        PrintAgent agent = find(principal.storeId(), principal.agentId());
        agent.heartbeat(request.agentVersion(), now);
        Map<UUID, Printer> printers = printerRepository.findAllByAgentIdAndActiveTrueOrderByNameAsc(agent.getId())
                .stream().collect(Collectors.toMap(Printer::getId, Function.identity()));
        for (AgentStatusRequest.PrinterStatusReport report : request.printers()) {
            Printer printer = printers.get(report.printerId());
            if (printer != null) {
                printer.reportStatus(report.status(), report.detail(), now);
            }
        }
    }

    /** Agentes da loja que estão mandando heartbeat, para o status das impressoras. */
    @Transactional(readOnly = true)
    public List<UUID> onlineAgentIds(UUID storeId) {
        Instant now = Instant.now(clock);
        return agentRepository.findAllByStoreIdAndRevokedAtIsNullOrderByCreatedAtAsc(storeId).stream()
                .filter(agent -> agent.isOnlineAt(now))
                .map(PrintAgent::getId)
                .toList();
    }

    void ensureExists(UUID storeId, UUID id) {
        find(storeId, id);
    }

    private PrintAgent find(UUID storeId, UUID id) {
        return agentRepository.findByIdAndStoreIdAndRevokedAtIsNull(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private Optional<AgentPairingCode> usableCode(String codeHash, Instant now) {
        return codeRepository.findAllByCodeHash(codeHash).stream().filter(code -> code.isUsableAt(now)).findFirst();
    }
}
