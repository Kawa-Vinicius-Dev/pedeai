package com.pedeai.payment.service;

import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.OrderPaymentRequest;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.event.OrderCreated;
import com.pedeai.order.service.OrderService;
import com.pedeai.payment.domain.Payment;
import com.pedeai.payment.domain.PaymentMethod;
import com.pedeai.payment.domain.PaymentMethodType;
import com.pedeai.payment.domain.PaymentStatus;
import com.pedeai.payment.dto.PaymentRequest;
import com.pedeai.payment.dto.PaymentResponse;
import com.pedeai.payment.dto.PaymentStatusRequest;
import com.pedeai.payment.repository.PaymentMethodRepository;
import com.pedeai.payment.repository.PaymentRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ForbiddenOperationException;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static com.pedeai.support.TestSecurity.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final PaymentMethod CASH = new PaymentMethod(STORE_ID, "Dinheiro", PaymentMethodType.CASH, 0, NOW);
    private static final PaymentMethod PIX = new PaymentMethod(STORE_ID, "Pix", PaymentMethodType.PIX, 1, NOW);

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentMethodRepository methodRepository;
    @Mock
    private OrderService orderService;

    private PaymentService service() {
        when(methodRepository.findByIdAndStoreId(CASH.getId(), STORE_ID)).thenReturn(Optional.of(CASH));
        when(methodRepository.findByIdAndStoreId(PIX.getId(), STORE_ID)).thenReturn(Optional.of(PIX));
        when(paymentRepository.save(any(Payment.class))).then(returnsFirstArg());
        return new PaymentService(paymentRepository, methodRepository, orderService, CLOCK);
    }

    @Test
    void paymentsInformedOnTheOrderAreSavedWithTheChange() {
        PaymentService service = service();

        service.onOrderCreated(created(12_180, List.of(
                new OrderPaymentRequest(PIX.getId(), 2_180L, null, true),
                new OrderPaymentRequest(CASH.getId(), 10_000L, 15_000L, false))));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getAllValues().get(1).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getAllValues().get(1).getChangeCents()).isEqualTo(5_000);
    }

    @Test
    void paymentsCannotPassTheOrderTotal() {
        PaymentService service = service();

        assertThatThrownBy(() -> service.onOrderCreated(created(10_000, List.of(
                new OrderPaymentRequest(PIX.getId(), 6_000L, null, true),
                new OrderPaymentRequest(CASH.getId(), 5_000L, null, false)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O valor passa do que falta pagar (R$ 40,00).");
    }

    @Test
    void inactiveOrForeignMethodIsRejected() {
        PaymentService service = service();
        UUID foreign = UUID.randomUUID();
        when(methodRepository.findByIdAndStoreId(foreign, STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.onOrderCreated(created(10_000, List.of(
                new OrderPaymentRequest(foreign, 1_000L, null, true)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(PaymentService.INVALID_METHOD);
    }

    @Test
    void laterPaymentOnlyCoversWhatIsLeft() {
        PaymentService service = service();
        when(orderService.get(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.READY, 10_000));
        when(paymentRepository.findAllByStoreIdAndOrderIdOrderByCreatedAtAscIdAsc(STORE_ID, ORDER_ID))
                .thenReturn(List.of(new Payment(STORE_ID, ORDER_ID, PIX, 7_000, null, true, USER_ID, NOW)));

        assertThatThrownBy(() -> service.register(user(Role.CASHIER), ORDER_ID,
                new PaymentRequest(CASH.getId(), 4_000L, null, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O valor passa do que falta pagar (R$ 30,00).");

        PaymentResponse payment = service.register(user(Role.CASHIER), ORDER_ID,
                new PaymentRequest(CASH.getId(), 3_000L, 5_000L, true));
        assertThat(payment.changeCents()).isEqualTo(2_000);
        assertThat(payment.methodName()).isEqualTo("Dinheiro");
    }

    @Test
    void cancelledOrderReceivesNothing() {
        PaymentService service = service();
        when(orderService.get(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.CANCELLED, 10_000));

        assertThatThrownBy(() -> service.register(user(Role.CASHIER), ORDER_ID,
                new PaymentRequest(PIX.getId(), 1_000L, null, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(PaymentService.CANCELLED_ORDER);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void onlyManagersCancelAReceivedPayment() {
        PaymentService service = service();
        when(orderService.get(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.COMPLETED, 10_000));
        Payment received = new Payment(STORE_ID, ORDER_ID, PIX, 10_000, null, true, USER_ID, NOW);
        when(paymentRepository.findByIdAndStoreIdAndOrderId(received.getId(), STORE_ID, ORDER_ID))
                .thenReturn(Optional.of(received));
        when(methodRepository.findById(PIX.getId())).thenReturn(Optional.of(PIX));

        assertThatThrownBy(() -> service.changeStatus(user(Role.CASHIER), ORDER_ID, received.getId(),
                new PaymentStatusRequest(PaymentStatus.CANCELLED)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage(PaymentService.REFUND_NEEDS_MANAGER);
        assertThat(service.changeStatus(user(Role.MANAGER), ORDER_ID, received.getId(),
                new PaymentStatusRequest(PaymentStatus.CANCELLED)).status()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void pendingCashIsReceivedOnDelivery() {
        PaymentService service = service();
        when(orderService.get(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.DISPATCHED, 10_000));
        Payment pending = new Payment(STORE_ID, ORDER_ID, CASH, 10_000, 15_000L, false, USER_ID, NOW);
        when(paymentRepository.findByIdAndStoreIdAndOrderId(pending.getId(), STORE_ID, ORDER_ID))
                .thenReturn(Optional.of(pending));
        when(methodRepository.findById(CASH.getId())).thenReturn(Optional.of(CASH));

        PaymentResponse paid = service.changeStatus(user(Role.CASHIER), ORDER_ID, pending.getId(),
                new PaymentStatusRequest(PaymentStatus.PAID));

        assertThat(paid.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.paidAt()).isEqualTo(NOW);
    }

    private static OrderCreated created(long total, List<OrderPaymentRequest> payments) {
        return new OrderCreated(STORE_ID, ORDER_ID, 1, OrderStatus.CONFIRMED, 0, total, payments, USER_ID);
    }

    private static OrderResponse order(OrderStatus status, long total) {
        return new OrderResponse(ORDER_ID, 1, LocalDate.of(2026, 9, 24), OrderType.DELIVERY, OrderSource.PEDEAI,
                status, null, "Maria", null, null, null, List.of(), total, 0, 0, 0, 0, total, NOW, null, null, null,
                null, null, null, null, 0);
    }

    private static CurrentUser user(Role role) {
        return new CurrentUser(USER_ID, STORE_ID, role, "Ana");
    }
}
