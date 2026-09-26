package com.pedeai.printing.service;

import com.pedeai.order.domain.ItemStatus;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.DeliveryAddressResponse;
import com.pedeai.order.dto.OrderItemOptionResponse;
import com.pedeai.order.dto.OrderItemResponse;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.payment.domain.PaymentMethodType;
import com.pedeai.payment.domain.PaymentOrigin;
import com.pedeai.payment.domain.PaymentStatus;
import com.pedeai.payment.dto.PaymentResponse;
import com.pedeai.printing.dto.TicketResponse;
import com.pedeai.printing.dto.TicketLineResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Golden files: o texto de cada documento, como sai no papel. Mudou o layout, muda aqui de propósito. */
class TicketLayoutTest {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Instant CREATED = Instant.parse("2026-09-24T22:42:00Z");
    private static final UUID KITCHEN = UUID.randomUUID();
    private static final UUID BAR = UUID.randomUUID();

    @Test
    void productionTicketIn80mm() {
        OrderResponse order = delivery();
        List<OrderItemResponse> kitchenItems = order.items().stream()
                .filter(item -> KITCHEN.equals(item.sectorId())).toList();
        String printed = paper(TicketLayout.production(order, "Cozinha", kitchenItems, 48, SAO_PAULO,
                CREATED.plusSeconds(15)));

        assertThat(printed).isEqualTo("""
                  |  ================================================
                2x|    COZINHA - PEDIDO 42
                  |  ================================================
                  |  DELIVERY                             24/09 19:42
                  |  Cliente: Maria Oliveira
                  |  ------------------------------------------------
                2x| *1x PIZZA GRANDE
                  |     + Calabresa
                  |     + Quatro queijos com borda recheada de
                  |       catupiry
                  |     OBS: sem cebola
                  |  ------------------------------------------------
                  |  OBS DO PEDIDO: Interfone quebrado
                  |  Impresso 19:42:15
                """);
    }

    @Test
    void orderTicketIn58mm() {
        String printed = paper(TicketLayout.orderTicket(delivery(), "Pizzaria da Ana", List.of(new PaymentResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Dinheiro", PaymentMethodType.CASH, 7490, 10000L, 2510L,
                PaymentStatus.PENDING, PaymentOrigin.LOCAL, null, CREATED)), 32, SAO_PAULO));

        assertThat(printed).isEqualTo("""
                2x|  Pizzaria da Ana
                  |       Não é documento fiscal
                  |  ================================
                  |  PEDIDO 42
                  |  DELIVERY
                  |  24/09/2026 19:42
                  |  --------------------------------
                  |  Cliente: Maria Oliveira
                  |  Tel: (11) 99999-0000
                  |  Rua das Flores, 120 - apto 3 -
                  |  Centro
                  |  Ref.: Portão azul
                  |  --------------------------------
                  |  QTD ITEM                   VALOR
                  |   1  PIZZA GRANDE           52,90
                  |        + Calabresa
                  |        + Quatro queijos com borda
                  |          recheada de catupiry
                  |      OBS: sem cebola
                  |   2  REFRIGERANTE LATA      14,00
                  |  --------------------------------
                  |  Subtotal                   66,90
                  |  Taxa de entrega             8,00
                  | *TOTAL                      74,90
                  |  --------------------------------
                  |  Dinheiro                   74,90
                  |    Troco para 100,00: levar 25,10
                  |  --------------------------------
                  |  OBS: Interfone quebrado
                  |  ================================
                """);
    }

    @Test
    void formatsBrazilianPhones() {
        assertThat(TicketLayout.phone("+5511999990000")).isEqualTo("(11) 99999-0000");
        assertThat(TicketLayout.phone("+551133334444")).isEqualTo("(11) 3333-4444");
        assertThat(TicketLayout.phone("+14155550100")).isEqualTo("+14155550100");
    }

    /** Como sai no papel: centraliza na largura da linha, e a fonte dupla vale por dois caracteres. */
    private static String paper(TicketResponse document) {
        return document.lines().stream().map(line -> {
            int width = line.big() ? document.columns() / 2 : document.columns();
            String text = line.align() == TicketLineResponse.Align.CENTER
                    ? " ".repeat((width - line.text().length()) / 2) + line.text() : line.text();
            return (line.big() ? "2x| " : "  | ") + (line.bold() ? "*" : " ") + text;
        }).collect(Collectors.joining("\n", "", "\n"));
    }

    private static OrderResponse delivery() {
        List<OrderItemResponse> items = List.of(
                new OrderItemResponse(UUID.randomUUID(), null, "500", "Pizza Grande", KITCHEN, 1, 0, 5290, 5290,
                        "sem cebola", ItemStatus.ACTIVE, List.of(
                        new OrderItemOptionResponse(null, "Sabores", "Calabresa", "101", 1, 4590),
                        new OrderItemOptionResponse(null, "Sabores", "Quatro queijos com borda recheada de catupiry",
                                "102", 1, 5290))),
                new OrderItemResponse(UUID.randomUUID(), null, "900", "Refrigerante lata", BAR, 2, 700, 0, 1400,
                        null, ItemStatus.ACTIVE, List.of()),
                new OrderItemResponse(UUID.randomUUID(), null, "901", "Suco", BAR, 1, 800, 0, 800,
                        null, ItemStatus.CANCELLED, List.of()));
        return new OrderResponse(UUID.randomUUID(), 42, LocalDate.of(2026, 9, 24), OrderType.DELIVERY,
                OrderSource.PEDEAI, OrderStatus.CONFIRMED, null, "Maria Oliveira", "+5511999990000",
                new DeliveryAddressResponse("Rua das Flores", "120", "apto 3", "Centro", "São Paulo", "SP", null,
                        "Portão azul"),
                "Interfone quebrado", items, 6690, 0, 800, 0, 0, 7490, CREATED, CREATED, null, null, null, null,
                null, null, 0);
    }
}
