package com.pedeai.order.controller;

import com.pedeai.customer.controller.CustomerController;
import com.pedeai.customer.controller.DeliveryZoneController;
import com.pedeai.customer.dto.CustomerResponse;
import com.pedeai.customer.dto.DeliveryZoneResponse;
import com.pedeai.customer.service.CustomerService;
import com.pedeai.customer.service.DeliveryZoneService;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.service.OrderService;
import com.pedeai.order.service.OrderStatusService;
import com.pedeai.payment.controller.OrderPaymentController;
import com.pedeai.payment.controller.PaymentMethodController;
import com.pedeai.payment.domain.PaymentMethodType;
import com.pedeai.payment.dto.PaymentMethodResponse;
import com.pedeai.payment.service.PaymentMethodService;
import com.pedeai.payment.service.PaymentService;
import com.pedeai.shared.config.TimeConfig;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Role;
import com.pedeai.shared.security.SecurityConfig;
import com.pedeai.shared.web.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static com.pedeai.support.TestSecurity.as;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({OrderController.class, OrderStatusController.class, OrderPaymentController.class,
        CustomerController.class, DeliveryZoneController.class, PaymentMethodController.class})
@Import({SecurityConfig.class, TimeConfig.class})
class OrderControllersTest {
    private static final UUID ORDER_ID = UUID.fromString("01a0d567-0000-7000-8000-000000000020");
    private static final UUID PRODUCT_ID = UUID.fromString("01a0d567-0000-7000-8000-000000000021");
    private static final String TAKEOUT_BODY = """
            {"type":"TAKEOUT","customer":{"name":"João","phone":null},"deliveryAddress":null,
             "items":[{"productId":"%s","quantity":1,"options":[],"notes":null}],
             "notes":null,"discountCents":0,"deliveryFeeCents":0,"payments":[]}
            """.formatted(PRODUCT_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private OrderStatusService orderStatusService;
    @MockitoBean
    private PaymentService paymentService;
    @MockitoBean
    private CustomerService customerService;
    @MockitoBean
    private DeliveryZoneService deliveryZoneService;
    @MockitoBean
    private PaymentMethodService paymentMethodService;

    @Test
    void cashierPlacesAnOrder() throws Exception {
        when(orderService.create(any(CurrentUser.class), any())).thenReturn(order(OrderStatus.CONFIRMED));

        mockMvc.perform(post("/api/orders").with(as(Role.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON).content(TAKEOUT_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/orders/" + ORDER_ID))
                .andExpect(jsonPath("$.number").value(12))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalCents").value(700));
    }

    @Test
    void waiterAndKitchenDoNotPlaceCounterOrders() throws Exception {
        for (Role role : List.of(Role.WAITER, Role.KITCHEN)) {
            mockMvc.perform(post("/api/orders").with(as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(TAKEOUT_BODY))
                    .andExpect(status().isForbidden());
        }
        verify(orderService, never()).create(any(), any());
    }

    @Test
    void orderWithoutItemsIsRejectedWithTheFieldMessage() throws Exception {
        mockMvc.perform(post("/api/orders").with(as(Role.CASHIER)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"TAKEOUT","items":[],"discountCents":0,"deliveryFeeCents":0,"payments":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.items").value("Adicione pelo menos um item."));
    }

    @Test
    void kitchenSeesTheBoardAndMovesOrders() throws Exception {
        when(orderService.listActive(STORE_ID)).thenReturn(List.of());
        when(orderStatusService.change(any(CurrentUser.class), eq(ORDER_ID), any()))
                .thenReturn(order(OrderStatus.READY));

        mockMvc.perform(get("/api/orders/active").with(as(Role.KITCHEN))).andExpect(status().isOk());
        mockMvc.perform(patch("/api/orders/{id}/status", ORDER_ID).with(as(Role.KITCHEN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READY\",\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void waiterDoesNotMoveCounterOrdersYet() throws Exception {
        mockMvc.perform(patch("/api/orders/{id}/status", ORDER_ID).with(as(Role.WAITER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READY\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void historyIsPagedAndValidatesThePage() throws Exception {
        when(orderService.search(STORE_ID, LocalDate.of(2026, 9, 24), OrderStatus.COMPLETED, null, "12", 0, 20))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/orders").with(as(Role.MANAGER))
                        .param("businessDate", "2026-09-24").param("status", "COMPLETED").param("q", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/orders").with(as(Role.MANAGER)).param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customersAreForWhoTakesOrders() throws Exception {
        when(customerService.search(eq(STORE_ID), eq("11999990000"), any(), any()))
                .thenReturn(new PageResponse<>(List.of(new CustomerResponse(UUID.randomUUID(), "Maria",
                        "+5511999990000", null, null, List.of())), 0, 20, 1, 1));

        mockMvc.perform(get("/api/customers").param("phone", "11999990000").with(as(Role.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Maria"));
        mockMvc.perform(get("/api/customers").param("phone", "11999990000").with(as(Role.KITCHEN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void settingsAreForManagersButEveryoneReadsThem() throws Exception {
        when(paymentMethodService.list(STORE_ID)).thenReturn(List.of(
                new PaymentMethodResponse(UUID.randomUUID(), "Dinheiro", PaymentMethodType.CASH, true)));
        when(deliveryZoneService.create(eq(STORE_ID), any()))
                .thenReturn(new DeliveryZoneResponse(UUID.randomUUID(), "Centro", 800, true));

        mockMvc.perform(get("/api/payment-methods").with(as(Role.WAITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Dinheiro"));
        mockMvc.perform(post("/api/payment-methods").with(as(Role.CASHIER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Vale\",\"type\":\"VOUCHER\",\"active\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/delivery-zones").with(as(Role.MANAGER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"neighborhood\":\"Centro\",\"feeCents\":800,\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.feeCents").value(800));
    }

    private static OrderResponse order(OrderStatus status) {
        return new OrderResponse(ORDER_ID, 12, LocalDate.of(2026, 9, 24), OrderType.TAKEOUT, OrderSource.PEDEAI,
                status, null, "João", null, null, null, List.of(), 700, 0, 0, 0, 0, 700, NOW, NOW, null, null, null,
                null, null, null, 0);
    }
}
