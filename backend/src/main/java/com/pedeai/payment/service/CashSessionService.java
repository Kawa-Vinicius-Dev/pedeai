package com.pedeai.payment.service;

import com.pedeai.payment.domain.CashMovement;
import com.pedeai.payment.domain.CashSession;
import com.pedeai.payment.domain.PaymentMethod;
import com.pedeai.payment.domain.PaymentMethodType;
import com.pedeai.payment.dto.CashLineResponse;
import com.pedeai.payment.dto.CashMovementRequest;
import com.pedeai.payment.dto.CashMovementResponse;
import com.pedeai.payment.dto.CashSessionResponse;
import com.pedeai.payment.dto.CashSessionSummaryResponse;
import com.pedeai.payment.dto.CloseCashSessionRequest;
import com.pedeai.payment.dto.OpenCashSessionRequest;
import com.pedeai.payment.repository.CashMovementRepository;
import com.pedeai.payment.repository.CashSessionRepository;
import com.pedeai.payment.repository.MethodTotal;
import com.pedeai.payment.repository.PaymentMethodRepository;
import com.pedeai.payment.repository.PaymentRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.money.Money;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.web.PageResponse;
import com.pedeai.store.dto.UserResponse;
import com.pedeai.store.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Caixa (docs/01-fluxos.md#caixa): abertura, sangria, suprimento e fechamento com conferência. O esperado de cada
 * forma de pagamento são os pagamentos recebidos na loja entre a abertura e o fechamento; no dinheiro, somam-se o
 * troco inicial e os suprimentos e descontam-se as sangrias. Pago online no marketplace não passa pelo caixa.
 */
@Service
public class CashSessionService {
    static final String NOT_FOUND = "Caixa não encontrado.";
    static final String ALREADY_OPEN = "Já existe um caixa aberto. Feche o atual antes de abrir outro.";
    static final String UNKNOWN_METHOD = "Forma de pagamento inválida na contagem.";

    private final CashSessionRepository sessionRepository;
    private final CashMovementRepository movementRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository methodRepository;
    private final UserService userService;
    private final Clock clock;

    public CashSessionService(CashSessionRepository sessionRepository, CashMovementRepository movementRepository,
                              PaymentRepository paymentRepository, PaymentMethodRepository methodRepository,
                              UserService userService, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.movementRepository = movementRepository;
        this.paymentRepository = paymentRepository;
        this.methodRepository = methodRepository;
        this.userService = userService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<CashSessionResponse> current(UUID storeId) {
        return sessionRepository.findByStoreIdAndStatus(storeId, CashSession.Status.OPEN).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CashSessionResponse get(UUID storeId, UUID id) {
        return toResponse(find(storeId, id));
    }

    /** Caixas do mais novo para o mais antigo. */
    @Transactional(readOnly = true)
    public PageResponse<CashSessionSummaryResponse> list(UUID storeId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "openedAt"));
        return PageResponse.from(sessionRepository.findAllByStoreId(storeId, pageable)
                .map(CashSessionSummaryResponse::from));
    }

    @Transactional
    public CashSessionResponse open(CurrentUser user, OpenCashSessionRequest request) {
        if (sessionRepository.findByStoreIdAndStatus(user.storeId(), CashSession.Status.OPEN).isPresent()) {
            throw new ConflictException(ALREADY_OPEN);
        }
        try {
            CashSession session = sessionRepository.saveAndFlush(new CashSession(user.storeId(),
                    request.openingAmountCents(), user.userId(), Instant.now(clock)));
            return toResponse(session);
        } catch (DataIntegrityViolationException e) {
            // Duas telas abrindo ao mesmo tempo: o UNIQUE do banco deixa só uma.
            throw new ConflictException(ALREADY_OPEN);
        }
    }

    @Transactional
    public CashSessionResponse addMovement(CurrentUser user, UUID sessionId, CashMovementRequest request) {
        CashSession session = find(user.storeId(), sessionId);
        session.requireOpen();
        if (request.type() == CashMovement.Type.WITHDRAWAL) {
            long cash = lines(session).stream().filter(line -> line.type() == PaymentMethodType.CASH)
                    .mapToLong(CashLineResponse::expectedCents).findFirst().orElse(0);
            if (request.amountCents() > cash) {
                throw new BusinessRuleException("A sangria passa do dinheiro que deve estar no caixa ("
                        + Money.format(Math.max(cash, 0)) + ").");
            }
        }
        movementRepository.save(new CashMovement(session, request.type(), request.amountCents(),
                request.reason().trim(), user.userId(), Instant.now(clock)));
        return toResponse(session);
    }

    @Transactional
    public CashSessionResponse close(CurrentUser user, UUID sessionId, CloseCashSessionRequest request) {
        CashSession session = find(user.storeId(), sessionId);
        session.requireOpen();
        Instant now = Instant.now(clock);
        List<CashLineResponse> lines = lines(session, now);
        Set<UUID> known = lines.stream().map(CashLineResponse::paymentMethodId).collect(Collectors.toSet());
        Map<UUID, Long> counted = request.counts().stream().collect(Collectors.toMap(
                CloseCashSessionRequest.Count::paymentMethodId, CloseCashSessionRequest.Count::countedCents,
                Long::sum));
        if (!known.containsAll(counted.keySet())) {
            throw new BusinessRuleException(UNKNOWN_METHOD);
        }
        String notes = request.notes() == null || request.notes().isBlank() ? null : request.notes().trim();
        session.close(lines.stream().map(line -> new CashSession.Count(line.paymentMethodId(), line.expectedCents(),
                counted.getOrDefault(line.paymentMethodId(), 0L))).toList(), notes, user.userId(), now);
        return toResponse(session);
    }

    private CashSession find(UUID storeId, UUID id) {
        return sessionRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private CashSessionResponse toResponse(CashSession session) {
        Map<UUID, String> names = userService.list(session.getStoreId()).stream()
                .collect(Collectors.toMap(UserResponse::id, UserResponse::name));
        List<CashMovementResponse> movements = movementRepository
                .findAllByCashSessionIdOrderByCreatedAtAscIdAsc(session.getId()).stream()
                .map(movement -> new CashMovementResponse(movement.getId(), movement.getType(),
                        movement.getAmountCents(), movement.getReason(), names.get(movement.getCreatedBy()),
                        movement.getCreatedAt()))
                .toList();
        List<CashLineResponse> lines = lines(session);
        long expected = lines.stream().mapToLong(CashLineResponse::expectedCents).sum();
        boolean closed = session.getStatus() == CashSession.Status.CLOSED;
        Long counted = closed ? lines.stream().mapToLong(CashLineResponse::countedCents).sum() : null;
        return new CashSessionResponse(session.getId(), session.getStatus(), session.getOpenedAt(),
                names.get(session.getOpenedBy()), session.getOpeningAmountCents(), session.getClosedAt(),
                session.getClosedBy() == null ? null : names.get(session.getClosedBy()), session.getNotes(), lines,
                movements, expected, counted, closed ? counted - expected : null);
    }

    private List<CashLineResponse> lines(CashSession session) {
        return lines(session, session.getClosedAt() == null ? Instant.now(clock) : session.getClosedAt());
    }

    /**
     * Uma linha por forma de pagamento ativa que passa pelo caixa, mais qualquer outra que recebeu no período. Fechado,
     * o esperado e o contado são os gravados no fechamento.
     */
    private List<CashLineResponse> lines(CashSession session, Instant until) {
        List<PaymentMethod> methods = methodRepository.findAllByStoreIdOrderBySortOrderAscNameAsc(session.getStoreId());
        Map<UUID, MethodTotal> received = paymentRepository.sumReceivedAtStore(session.getStoreId(),
                session.getOpenedAt(), until).stream()
                .collect(Collectors.toMap(MethodTotal::paymentMethodId, Function.identity()));
        Map<UUID, CashSession.Count> counts = session.getCounts().stream()
                .collect(Collectors.toMap(CashSession.Count::paymentMethodId, Function.identity()));
        UUID drawer = methods.stream().filter(method -> method.getType() == PaymentMethodType.CASH)
                .map(PaymentMethod::getId).findFirst().orElse(null);
        long drawerAdjustments = session.getOpeningAmountCents() + movementRepository
                .findAllByCashSessionIdOrderByCreatedAtAscIdAsc(session.getId()).stream()
                .mapToLong(CashMovement::signedCents).sum();
        boolean closed = session.getStatus() == CashSession.Status.CLOSED;

        List<CashLineResponse> lines = new ArrayList<>();
        for (PaymentMethod method : methods) {
            boolean passesThroughDrawer = method.isActive() && method.getType() != PaymentMethodType.ONLINE;
            boolean shown = closed ? counts.containsKey(method.getId())
                    : passesThroughDrawer || received.containsKey(method.getId()) || method.getId().equals(drawer);
            if (!shown) {
                continue;
            }
            MethodTotal total = received.get(method.getId());
            long adjustments = method.getId().equals(drawer) ? drawerAdjustments : 0;
            CashSession.Count count = counts.get(method.getId());
            long expected = closed ? count.expectedCents() : (total == null ? 0 : total.totalCents()) + adjustments;
            Long countedCents = closed ? count.countedCents() : null;
            lines.add(new CashLineResponse(method.getId(), method.getName(), method.getType(), expected - adjustments,
                    total == null ? 0 : total.payments(), expected, countedCents,
                    closed ? countedCents - expected : null));
        }
        return lines;
    }
}
