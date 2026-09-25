package com.pedeai.payment.service;

import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.dto.OrderPaymentRequest;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.event.OrderCreated;
import com.pedeai.order.service.OrderService;
import com.pedeai.payment.domain.Payment;
import com.pedeai.payment.domain.PaymentMethod;
import com.pedeai.payment.domain.PaymentStatus;
import com.pedeai.payment.dto.PaymentRequest;
import com.pedeai.payment.dto.PaymentResponse;
import com.pedeai.payment.dto.PaymentStatusRequest;
import com.pedeai.payment.repository.PaymentMethodRepository;
import com.pedeai.payment.repository.PaymentRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ForbiddenOperationException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.money.Money;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Role;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pagamentos do pedido: os informados no lançamento e os recebidos depois (na entrega, na retirada). */
@Service
public class PaymentService {
    static final String NOT_FOUND = "Pagamento não encontrado.";
    static final String INVALID_METHOD = "Forma de pagamento inválida.";
    static final String CANCELLED_ORDER = "Pedido cancelado não recebe pagamento.";
    static final String REFUND_NEEDS_MANAGER = "Só gerente ou dono pode cancelar um pagamento já recebido.";
    static final String REOPEN_NOT_ALLOWED = "Um pagamento não volta a ficar pendente.";

    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository methodRepository;
    private final OrderService orderService;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository, PaymentMethodRepository methodRepository,
                          OrderService orderService, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.methodRepository = methodRepository;
        this.orderService = orderService;
        this.clock = clock;
    }

    /** Pagamentos informados ao lançar o pedido. Na mesma transação: se forem inválidos, o pedido não é criado. */
    @EventListener
    public void onOrderCreated(OrderCreated event) {
        long remaining = event.totalCents();
        Instant now = Instant.now(clock);
        for (OrderPaymentRequest request : event.payments()) {
            PaymentMethod method = activeMethod(event.storeId(), request.paymentMethodId());
            checkFits(request.amountCents(), remaining);
            paymentRepository.save(new Payment(event.storeId(), event.orderId(), method, request.amountCents(),
                    request.changeForCents(), request.paid(), event.createdBy(), now));
            remaining -= request.amountCents();
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> list(UUID storeId, UUID orderId) {
        orderService.get(storeId, orderId);
        return toResponses(storeId, paymentRepository.findAllByStoreIdAndOrderIdOrderByCreatedAtAscIdAsc(storeId,
                orderId));
    }

    @Transactional
    public PaymentResponse register(CurrentUser user, UUID orderId, PaymentRequest request) {
        OrderResponse order = orderService.get(user.storeId(), orderId);
        if (order.status() == OrderStatus.CANCELLED) {
            throw new BusinessRuleException(CANCELLED_ORDER);
        }
        PaymentMethod method = activeMethod(user.storeId(), request.paymentMethodId());
        long alreadyCounted = paymentRepository.findAllByStoreIdAndOrderIdOrderByCreatedAtAscIdAsc(user.storeId(),
                orderId).stream().filter(Payment::counts).mapToLong(Payment::getAmountCents).sum();
        checkFits(request.amountCents(), order.totalCents() - alreadyCounted);
        Payment payment = paymentRepository.save(new Payment(user.storeId(), orderId, method, request.amountCents(),
                request.changeForCents(), request.paid(), user.userId(), Instant.now(clock)));
        return PaymentResponse.from(payment, method);
    }

    @Transactional
    public PaymentResponse changeStatus(CurrentUser user, UUID orderId, UUID paymentId, PaymentStatusRequest request) {
        orderService.get(user.storeId(), orderId);
        Payment payment = paymentRepository.findByIdAndStoreIdAndOrderId(paymentId, user.storeId(), orderId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        Instant now = Instant.now(clock);
        switch (request.status()) {
            case PAID -> payment.markPaid(user.userId(), now);
            case CANCELLED -> {
                boolean manager = user.role() == Role.OWNER || user.role() == Role.MANAGER;
                if (payment.getStatus() == PaymentStatus.PAID && !manager) {
                    throw new ForbiddenOperationException(REFUND_NEEDS_MANAGER);
                }
                payment.cancel(now);
            }
            case PENDING -> throw new BusinessRuleException(REOPEN_NOT_ALLOWED);
        }
        PaymentMethod method = methodRepository.findById(payment.getPaymentMethodId()).orElseThrow();
        return PaymentResponse.from(payment, method);
    }

    private PaymentMethod activeMethod(UUID storeId, UUID methodId) {
        return methodRepository.findByIdAndStoreId(methodId, storeId)
                .filter(PaymentMethod::isActive)
                .orElseThrow(() -> new BusinessRuleException(INVALID_METHOD));
    }

    private static void checkFits(long amountCents, long remainingCents) {
        if (amountCents > remainingCents) {
            throw new BusinessRuleException(remainingCents <= 0
                    ? "Este pedido já está todo pago."
                    : "O valor passa do que falta pagar (" + Money.format(remainingCents) + ").");
        }
    }

    private List<PaymentResponse> toResponses(UUID storeId, List<Payment> payments) {
        List<UUID> methodIds = payments.stream().map(Payment::getPaymentMethodId).distinct().toList();
        Map<UUID, PaymentMethod> methods = methodRepository.findAllByStoreIdAndIdIn(storeId, methodIds).stream()
                .collect(Collectors.toMap(PaymentMethod::getId, Function.identity()));
        return payments.stream().map(payment -> PaymentResponse.from(payment, methods.get(payment.getPaymentMethodId())))
                .toList();
    }
}
