package com.pedeai.order.service;

import com.pedeai.catalog.dto.PriceQuoteRequest;
import com.pedeai.catalog.dto.PriceQuoteResponse;
import com.pedeai.catalog.service.ProductService;
import com.pedeai.customer.dto.AddressRequest;
import com.pedeai.customer.dto.CustomerLink;
import com.pedeai.customer.service.CustomerService;
import com.pedeai.order.domain.Order;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.domain.OrderStatusHistory;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.CreateOrderRequest;
import com.pedeai.order.dto.OrderCustomerRequest;
import com.pedeai.order.dto.OrderItemRequest;
import com.pedeai.order.dto.OrderPaymentRequest;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.event.OrderCreated;
import com.pedeai.order.repository.OrderRepository;
import com.pedeai.order.repository.OrderStatusHistoryRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ForbiddenOperationException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Role;
import com.pedeai.store.dto.StoreResponse;
import com.pedeai.store.service.StoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static com.pedeai.support.TestSecurity.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {
    private static final UUID PIZZA = UUID.randomUUID();
    private static final UUID SODA = UUID.randomUUID();
    private static final UUID CALABRESA = UUID.randomUUID();
    private static final UUID FOUR_CHEESE = UUID.randomUUID();
    private static final UUID KITCHEN = UUID.randomUUID();
    private static final UUID CASH = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final AddressRequest ADDRESS =
            new AddressRequest(null, "Rua das Flores", "120", "Ap 32", "Centro", "São Paulo", "SP", null, null);
    private static final CurrentUser CASHIER = new CurrentUser(USER_ID, STORE_ID, Role.CASHIER, "Caio");
    private static final CurrentUser MANAGER = new CurrentUser(USER_ID, STORE_ID, Role.MANAGER, "Ana");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusHistoryRepository historyRepository;
    @Mock
    private OrderNumberService numberService;
    @Mock
    private ProductService productService;
    @Mock
    private CustomerService customerService;
    @Mock
    private StoreService storeService;
    @Mock
    private ApplicationEventPublisher events;

    private OrderService service() {
        when(orderRepository.save(any(Order.class))).then(returnsFirstArg());
        when(numberService.next(eq(STORE_ID), any(LocalDate.class))).thenReturn(42);
        when(customerService.recordFromOrder(eq(STORE_ID), any(), any(), any()))
                .thenReturn(new CustomerLink(CUSTOMER, UUID.randomUUID(), "+5511999990000"));
        when(productService.quote(eq(STORE_ID), eq(PIZZA), any(PriceQuoteRequest.class))).thenAnswer(call -> {
            PriceQuoteRequest request = call.getArgument(2);
            return new PriceQuoteResponse(PIZZA, "Pizza Grande", "500", KITCHEN, request.quantity(), 0, 5290, 5290,
                    5290L * request.quantity(), List.of(
                    new PriceQuoteResponse.QuotedOptionResponse(CALABRESA, UUID.randomUUID(), "Sabores", "Calabresa",
                            "101", 1, 4590),
                    new PriceQuoteResponse.QuotedOptionResponse(FOUR_CHEESE, UUID.randomUUID(), "Sabores",
                            "Quatro queijos", "102", 1, 5290)));
        });
        when(productService.quote(eq(STORE_ID), eq(SODA), any(PriceQuoteRequest.class)))
                .thenReturn(new PriceQuoteResponse(SODA, "Refrigerante lata", "900", null, 1, 700, 0, 700, 700, List.of()));
        store(true, false);
        return new OrderService(orderRepository, historyRepository, numberService, productService, customerService,
                storeService, events, CLOCK);
    }

    private void store(boolean autoConfirm, boolean startPreparation) {
        when(storeService.get(STORE_ID)).thenReturn(new StoreResponse(STORE_ID, "Pizzaria Bella", null, null,
                "America/Sao_Paulo", LocalTime.of(5, 0), 1000, autoConfirm, startPreparation));
    }

    @Test
    void deliveryOrderPricesItemsFromTheMenuAndKeepsASnapshot() {
        OrderService service = service();

        OrderResponse order = service.create(CASHIER, delivery(0, 800, List.of(
                new OrderPaymentRequest(CASH, 11_380L, 15_000L, false))));

        // 2 pizzas meio a meio (maior valor: R$ 52,90) + 1 refrigerante R$ 7,00 + entrega R$ 8,00
        assertThat(order.subtotalCents()).isEqualTo(2 * 5290 + 700);
        assertThat(order.deliveryFeeCents()).isEqualTo(800);
        assertThat(order.totalCents()).isEqualTo(2 * 5290 + 700 + 800);
        assertThat(order.number()).isEqualTo(42);
        assertThat(order.items()).hasSize(2);
        assertThat(order.items().getFirst().name()).isEqualTo("Pizza Grande");
        assertThat(order.items().getFirst().sectorId()).isEqualTo(KITCHEN);
        assertThat(order.items().getFirst().options()).extracting("name").containsExactly("Calabresa", "Quatro queijos");
        assertThat(order.customerId()).isEqualTo(CUSTOMER);
        assertThat(order.customerPhone()).isEqualTo("+5511999990000");
        assertThat(order.deliveryAddress().street()).isEqualTo("Rua das Flores");
        assertThat(order.deliveryAddress().complement()).isEqualTo("Ap 32");
        assertThat(order.items().getFirst().notes()).isEqualTo("sem cebola");
        // O relógio de teste marca 09:00 em São Paulo, depois da virada das 05:00: é o próprio dia.
        assertThat(order.businessDate()).isEqualTo(LocalDate.of(2026, 9, 24));
    }

    @Test
    void storeThatAutoConfirmsStartsTheOrderConfirmedAndPublishesPayments() {
        OrderService service = service();
        List<OrderPaymentRequest> payments = List.of(new OrderPaymentRequest(CASH, 11_380L, 15_000L, false));

        OrderResponse order = service.create(CASHIER, delivery(0, 800, payments));

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.confirmedAt()).isNotNull();
        List<OrderStatusHistory> history = savedTimeline();
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getFromStatus()).isNull();
        assertThat(history.getFirst().getToStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(history.getFirst().getActorName()).isEqualTo("Caio");
        ArgumentCaptor<OrderCreated> created = ArgumentCaptor.forClass(OrderCreated.class);
        verify(events).publishEvent(created.capture());
        assertThat(created.getValue().payments()).isEqualTo(payments);
        assertThat(created.getValue().totalCents()).isEqualTo(order.totalCents());
    }

    @Test
    void storeWithoutKitchenScreenPutsTheOrderInPreparationRightAway() {
        OrderService service = service();
        store(true, true);

        OrderResponse order = service.create(CASHIER, delivery(0, 800, List.of()));

        assertThat(order.status()).isEqualTo(OrderStatus.IN_PREPARATION);
        assertThat(savedTimeline()).extracting(OrderStatusHistory::getToStatus)
                .containsExactly(OrderStatus.CONFIRMED, OrderStatus.IN_PREPARATION);
    }

    @Test
    void storeThatDoesNotAutoConfirmLeavesTheOrderReceived() {
        OrderService service = service();
        store(false, false);

        OrderResponse order = service.create(CASHIER, delivery(0, 800, List.of()));

        assertThat(order.status()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(order.confirmedAt()).isNull();
    }

    @Test
    void takeoutWithJustANameDoesNotCreateACustomer() {
        OrderService service = service();

        OrderResponse order = service.create(CASHIER, new CreateOrderRequest(OrderType.TAKEOUT,
                new OrderCustomerRequest("João", null), null, List.of(soda()), null, 0L, 0L, List.of()));

        assertThat(order.customerName()).isEqualTo("João");
        assertThat(order.customerId()).isNull();
        assertThat(order.totalCents()).isEqualTo(700);
        verify(customerService, never()).recordFromOrder(any(), any(), any(), any());
    }

    @Test
    void deliveryNeedsPhoneAndAddress() {
        OrderService service = service();

        assertThatThrownBy(() -> service.create(CASHIER, new CreateOrderRequest(OrderType.DELIVERY,
                new OrderCustomerRequest("Maria", null), ADDRESS, List.of(soda()), null, 0L, 800L, List.of())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(OrderService.DELIVERY_NEEDS_CUSTOMER);
        assertThatThrownBy(() -> service.create(CASHIER, new CreateOrderRequest(OrderType.DELIVERY,
                new OrderCustomerRequest("Maria", "11999990000"), null, List.of(soda()), null, 0L, 800L, List.of())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(OrderService.DELIVERY_NEEDS_ADDRESS);
    }

    @Test
    void takeoutHasNoAddressNorDeliveryFee() {
        OrderService service = service();

        assertThatThrownBy(() -> service.create(CASHIER, new CreateOrderRequest(OrderType.TAKEOUT, null, null,
                List.of(soda()), null, 0L, 500L, List.of())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(OrderService.TAKEOUT_WITHOUT_ADDRESS);
    }

    @Test
    void onlyManagersGiveDiscounts() {
        OrderService service = service();

        assertThatThrownBy(() -> service.create(CASHIER, delivery(500, 800, List.of())))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage(OrderService.DISCOUNT_NEEDS_MANAGER);

        OrderResponse order = service.create(MANAGER, delivery(500, 800, List.of()));
        assertThat(order.discountCents()).isEqualTo(500);
        assertThat(order.totalCents()).isEqualTo(2 * 5290 + 700 - 500 + 800);
    }

    @Test
    void productThatLeftTheMenuStopsTheOrderWithAClearMessage() {
        OrderService service = service();
        UUID gone = UUID.randomUUID();
        when(productService.quote(eq(STORE_ID), eq(gone), any())).thenThrow(new ResourceNotFoundException("x"));

        assertThatThrownBy(() -> service.create(CASHIER, new CreateOrderRequest(OrderType.TAKEOUT, null, null,
                List.of(new OrderItemRequest(gone, 1, List.of(), null)), null, 0L, 0L, List.of())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(OrderService.PRODUCT_NOT_FOUND);
    }

    @Test
    void dineInGoesThroughTheTab() {
        OrderService service = service();

        assertThatThrownBy(() -> service.create(CASHIER, new CreateOrderRequest(OrderType.DINE_IN, null, null,
                List.of(soda()), null, 0L, 0L, List.of())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(OrderService.DINE_IN_BY_TAB);
    }

    @SuppressWarnings("unchecked")
    private List<OrderStatusHistory> savedTimeline() {
        ArgumentCaptor<List<OrderStatusHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(historyRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static CreateOrderRequest delivery(long discount, long fee, List<OrderPaymentRequest> payments) {
        OrderItemRequest halfAndHalf = new OrderItemRequest(PIZZA, 2, List.of(
                new PriceQuoteRequest.OptionChoice(CALABRESA, 1), new PriceQuoteRequest.OptionChoice(FOUR_CHEESE, 1)),
                "  sem cebola ");
        return new CreateOrderRequest(OrderType.DELIVERY, new OrderCustomerRequest(" Maria ", "(11) 99999-0000"),
                ADDRESS, List.of(halfAndHalf, soda()), "Interfone quebrado", discount, fee, payments);
    }

    private static OrderItemRequest soda() {
        return new OrderItemRequest(SODA, 1, List.of(), null);
    }
}
