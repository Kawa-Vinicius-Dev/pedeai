package com.pedeai.printing.service;

import com.pedeai.order.domain.ItemStatus;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.DeliveryAddressResponse;
import com.pedeai.order.dto.OrderItemOptionResponse;
import com.pedeai.order.dto.OrderItemResponse;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.payment.domain.CashMovement;
import com.pedeai.payment.domain.CashSession;
import com.pedeai.payment.domain.PaymentStatus;
import com.pedeai.payment.dto.CashLineResponse;
import com.pedeai.payment.dto.CashMovementResponse;
import com.pedeai.payment.dto.CashSessionResponse;
import com.pedeai.payment.dto.PaymentResponse;
import com.pedeai.printing.domain.DocumentType;
import com.pedeai.printing.dto.TicketResponse;
import com.pedeai.printing.dto.TicketLineResponse;
import com.pedeai.printing.dto.TicketLineResponse.Align;
import com.pedeai.shared.money.Money;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Os layouts de cada documento, como nos exemplos de docs/04-impressao.md. Só texto: sem banco e sem relógio. */
final class TicketLayout {
    private static final Locale BRAZIL = Locale.of("pt", "BR");
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter CLOCK_SHORT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Map<OrderType, String> TYPES = Map.of(
            OrderType.DELIVERY, "DELIVERY", OrderType.TAKEOUT, "RETIRADA", OrderType.DINE_IN, "MESA");
    private static final Map<OrderSource, String> SOURCES = Map.of(
            OrderSource.PEDEAI, "", OrderSource.DIGITAL_MENU, "Cardápio", OrderSource.IFOOD, "iFood",
            OrderSource.NINETY_NINE_FOOD, "99Food");

    private TicketLayout() {
    }

    /** Ticket de produção: só os itens do setor, letra grande, sem preço. */
    static TicketResponse production(OrderResponse order, String sectorName, List<OrderItemResponse> items,
                                     int columns, ZoneId zone, Instant printedAt) {
        Lines out = new Lines(columns);
        out.rule('=');
        out.big(sectorName.toUpperCase(BRAZIL) + " - PEDIDO " + order.number(), Align.CENTER);
        out.rule('=');
        out.pair(typeAndSource(order), SHORT.format(order.createdAt().atZone(zone)));
        if (order.customerName() != null) {
            out.text("Cliente: " + order.customerName());
        }
        out.rule('-');
        for (OrderItemResponse item : items) {
            out.bigBold(item.quantity() + "x " + item.name().toUpperCase(BRAZIL));
            item.options().forEach(option -> out.hanging("   + ", option(option)));
            if (item.notes() != null) {
                out.hanging("   OBS: ", item.notes());
            }
        }
        out.rule('-');
        if (order.notes() != null) {
            out.text("OBS DO PEDIDO: " + order.notes());
        }
        out.text("Impresso " + CLOCK.format(printedAt.atZone(zone)));
        return out.document(DocumentType.PRODUCTION_TICKET);
    }

    /** Aviso de cancelamento para o setor: bem visível, com o motivo e o que não preparar. */
    static TicketResponse cancellation(OrderResponse order, String sectorName, List<OrderItemResponse> items,
                                       int columns, ZoneId zone, Instant cancelledAt) {
        Lines out = new Lines(columns);
        out.rule('#');
        out.big("CANCELADO", Align.CENTER);
        out.boldCenter("*** NÃO PREPARAR ***");
        out.rule('#');
        out.pair("PEDIDO " + order.number() + " - " + sectorName.toUpperCase(BRAZIL),
                CLOCK.format(cancelledAt.atZone(zone)).substring(0, 5));
        if (order.cancelReason() != null) {
            out.text("Motivo: " + order.cancelReason());
        }
        out.rule('-');
        items.forEach(item -> out.bold(item.quantity() + "x " + item.name().toUpperCase(BRAZIL)));
        out.rule('#');
        return out.document(DocumentType.CANCELLATION_TICKET);
    }

    /** Via completa: itens com valores, totais, cliente, endereço e pagamento. */
    static TicketResponse orderTicket(OrderResponse order, String storeName, List<PaymentResponse> payments,
                                      int columns, ZoneId zone) {
        Lines out = new Lines(columns);
        out.big(storeName, Align.CENTER);
        out.center("Não é documento fiscal");
        out.rule('=');
        out.pair("PEDIDO " + order.number(), sourceLabel(order));
        out.text(TYPES.get(order.type()));
        out.text(FULL.format(order.createdAt().atZone(zone)));
        customer(out, order);
        out.rule('-');
        out.pair("QTD ITEM", "VALOR");
        for (OrderItemResponse item : order.items().stream().filter(i -> i.status() == ItemStatus.ACTIVE).toList()) {
            out.block(String.format("%2d  ", item.quantity()), item.name().toUpperCase(BRAZIL),
                    Money.plain(item.totalCents()), false);
            item.options().forEach(option -> out.hanging("      + ", option(option)));
            if (item.notes() != null) {
                out.hanging("    OBS: ", item.notes());
            }
        }
        out.rule('-');
        out.pair("Subtotal", Money.plain(order.subtotalCents()));
        if (order.deliveryFeeCents() > 0) {
            out.pair("Taxa de entrega", Money.plain(order.deliveryFeeCents()));
        }
        if (order.additionalFeeCents() > 0) {
            out.pair("Taxa adicional", Money.plain(order.additionalFeeCents()));
        }
        if (order.discountCents() > 0) {
            out.pair("Desconto", "-" + Money.plain(order.discountCents()));
        }
        out.boldPair("TOTAL", Money.plain(order.totalCents()));
        out.rule('-');
        if (payments.isEmpty()) {
            out.text("Pagamento: não informado");
        }
        for (PaymentResponse payment : payments) {
            String paid = payment.status() == PaymentStatus.PAID ? " (pago)" : "";
            out.pair(payment.methodName() + paid, Money.plain(payment.amountCents()));
            if (payment.changeForCents() != null) {
                out.hanging("  ", "Troco para " + Money.plain(payment.changeForCents()) + ": levar "
                        + Money.plain(payment.changeCents()));
            }
        }
        if (order.notes() != null) {
            out.rule('-');
            out.text("OBS: " + order.notes());
        }
        out.rule('=');
        return out.document(DocumentType.ORDER_TICKET);
    }

    /** Relatório do caixa: abertura, movimentos e a conferência por forma de pagamento. */
    static TicketResponse cashReport(CashSessionResponse session, String storeName, int columns, ZoneId zone,
                                     Instant now) {
        boolean closed = session.status() == CashSession.Status.CLOSED;
        Lines out = new Lines(columns);
        out.big(storeName, Align.CENTER);
        out.boldCenter(closed ? "FECHAMENTO DE CAIXA" : "PARCIAL DO CAIXA (ABERTO)");
        out.rule('=');
        out.text("Abertura: " + FULL.format(session.openedAt().atZone(zone))
                + (session.openedByName() == null ? "" : " - " + session.openedByName()));
        if (closed) {
            out.text("Fechamento: " + FULL.format(session.closedAt().atZone(zone))
                    + (session.closedByName() == null ? "" : " - " + session.closedByName()));
        }
        out.pair("Troco inicial", Money.plain(session.openingAmountCents()));
        if (!session.movements().isEmpty()) {
            out.rule('-');
            for (CashMovementResponse movement : session.movements()) {
                boolean withdrawal = movement.type() == CashMovement.Type.WITHDRAWAL;
                out.pair((withdrawal ? "Sangria " : "Suprimento ") + CLOCK_SHORT.format(movement.createdAt()
                        .atZone(zone)), (withdrawal ? "-" : "") + Money.plain(movement.amountCents()));
                out.hanging("  ", movement.reason());
            }
        }
        out.rule('-');
        for (CashLineResponse line : session.lines()) {
            out.bold(line.name().toUpperCase(BRAZIL) + " (" + line.payments() + ")");
            out.pair("  Recebido", Money.plain(line.paymentsCents()));
            out.pair("  Esperado", Money.plain(line.expectedCents()));
            if (closed) {
                out.pair("  Contado", Money.plain(line.countedCents()));
                out.pair("  Diferença", signed(line.differenceCents()));
            }
        }
        out.rule('-');
        out.boldPair("TOTAL ESPERADO", Money.plain(session.expectedCents()));
        if (closed) {
            out.boldPair("TOTAL CONTADO", Money.plain(session.countedCents()));
            out.boldPair("DIFERENÇA", signed(session.differenceCents()));
        }
        if (session.notes() != null) {
            out.rule('-');
            out.text("OBS: " + session.notes());
        }
        out.rule('=');
        out.text("Impresso " + FULL.format(now.atZone(zone)));
        return out.document(DocumentType.CASH_REPORT);
    }

    /** Sobra com "+", falta com "-". */
    private static String signed(long cents) {
        return (cents > 0 ? "+" : "") + Money.plain(cents);
    }

    /** O mesmo documento com a faixa "REIMPRESSÃO" e o horário em que saiu a primeira vez. */
    static TicketResponse reprint(TicketResponse ticket, Instant original, ZoneId zone) {
        Lines out = new Lines(ticket.columns());
        out.rule('*');
        out.boldCenter("*** REIMPRESSÃO ***");
        out.center("Original: " + SHORT.format(original.atZone(zone)));
        out.rule('*');
        List<TicketLineResponse> lines = new ArrayList<>(out.lines);
        lines.addAll(ticket.lines());
        return new TicketResponse(ticket.documentType(), ticket.columns(), List.copyOf(lines));
    }

    private static void customer(Lines out, OrderResponse order) {
        DeliveryAddressResponse address = order.deliveryAddress();
        if (order.customerName() == null && order.customerPhone() == null && address == null) {
            return;
        }
        out.rule('-');
        if (order.customerName() != null) {
            out.text("Cliente: " + order.customerName());
        }
        if (order.customerPhone() != null) {
            out.text("Tel: " + phone(order.customerPhone()));
        }
        if (address != null) {
            out.text(Stream.of(address.street() + ", " + address.number(), address.complement(),
                            address.neighborhood())
                    .filter(part -> part != null && !part.isBlank())
                    .collect(Collectors.joining(" - ")));
            if (address.reference() != null) {
                out.text("Ref.: " + address.reference());
            }
        }
    }

    private static String typeAndSource(OrderResponse order) {
        String source = sourceLabel(order);
        return source.isEmpty() ? TYPES.get(order.type()) : TYPES.get(order.type()) + " - " + source;
    }

    /** "iFood 7391": o número que o entregador e o cliente veem no aplicativo. Pedido próprio: vazio. */
    private static String sourceLabel(OrderResponse order) {
        String source = SOURCES.get(order.source());
        return source.isEmpty() || order.externalDisplayId() == null ? source : source + " " + order.externalDisplayId();
    }

    private static String option(OrderItemOptionResponse option) {
        return option.quantity() > 1 ? option.quantity() + "x " + option.name() : option.name();
    }

    /** "+5511999990000" vira "(11) 99999-0000". Telefone de fora do Brasil sai como veio. */
    static String phone(String e164) {
        if (!e164.startsWith("+55") || e164.length() < 13) {
            return e164;
        }
        String national = e164.substring(3);
        return "(" + national.substring(0, 2) + ") " + national.substring(2, national.length() - 4) + "-"
                + national.substring(national.length() - 4);
    }

    /** Monta as linhas quebrando o texto por palavra na largura do papel. */
    private static final class Lines {
        private final int columns;
        private final List<TicketLineResponse> lines = new ArrayList<>();

        Lines(int columns) {
            this.columns = columns;
        }

        void text(String text) {
            wrap(text, columns).forEach(line -> add(line, Align.LEFT, false, false));
        }

        void center(String text) {
            wrap(text, columns).forEach(line -> add(line, Align.CENTER, false, false));
        }

        void bold(String text) {
            wrap(text, columns).forEach(line -> add(line, Align.LEFT, true, false));
        }

        void boldCenter(String text) {
            wrap(text, columns).forEach(line -> add(line, Align.CENTER, true, false));
        }

        void big(String text, Align align) {
            wrap(text, columns / 2).forEach(line -> add(line, align, false, true));
        }

        void bigBold(String text) {
            wrap(text, columns / 2).forEach(line -> add(line, Align.LEFT, true, true));
        }

        /** O prefixo só na primeira linha; as seguintes começam alinhadas com o texto. */
        void hanging(String prefix, String text) {
            block(prefix, text, "", false);
        }

        void rule(char character) {
            add(String.valueOf(character).repeat(columns), Align.LEFT, false, false);
        }

        void pair(String left, String right) {
            block("", left, right, false);
        }

        void boldPair(String left, String right) {
            block("", left, right, true);
        }

        /** Prefixo, texto com recuo pendente e, se houver, o valor encostado na direita da primeira linha. */
        void block(String prefix, String text, String right, boolean bold) {
            int width = columns - prefix.length() - (right.isEmpty() ? 0 : right.length() + 1);
            List<String> parts = wrap(text, width);
            String first = prefix + parts.getFirst();
            String padding = right.isEmpty() ? "" : " ".repeat(columns - first.length() - right.length());
            add(first + padding + right, Align.LEFT, bold, false);
            String indent = " ".repeat(prefix.length());
            parts.stream().skip(1).forEach(line -> add(indent + line, Align.LEFT, bold, false));
        }

        private void add(String text, Align align, boolean bold, boolean big) {
            lines.add(new TicketLineResponse(text, align, bold, big));
        }

        TicketResponse document(DocumentType type) {
            return new TicketResponse(type, columns, List.copyOf(lines));
        }

        /** Quebra por palavra. Palavra maior que a linha é cortada no meio, para nunca passar da largura. */
        static List<String> wrap(String text, int width) {
            List<String> result = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.strip().split("\\s+")) {
                while (word.length() > width) {
                    if (!line.isEmpty()) {
                        result.add(line.toString());
                        line.setLength(0);
                    }
                    result.add(word.substring(0, width));
                    word = word.substring(width);
                }
                if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                    result.add(line.toString());
                    line.setLength(0);
                }
                if (!line.isEmpty()) {
                    line.append(' ');
                }
                line.append(word);
            }
            if (!line.isEmpty() || result.isEmpty()) {
                result.add(line.toString());
            }
            return result;
        }
    }
}
