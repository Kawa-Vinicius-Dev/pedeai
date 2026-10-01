package com.pedeai.integration.opendelivery;

import com.pedeai.catalog.dto.ProductResponse;
import com.pedeai.catalog.dto.SectorResponse;
import com.pedeai.catalog.service.ProductService;
import com.pedeai.catalog.service.SectorService;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.DeliveryAddressResponse;
import com.pedeai.order.dto.MarketplaceOrderRequest;
import com.pedeai.order.dto.OrderPaymentRequest;
import com.pedeai.payment.domain.PaymentMethodType;
import com.pedeai.payment.dto.PaymentMethodResponse;
import com.pedeai.payment.service.PaymentMethodService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pedido Open Delivery (schema {@code Order} da especificação v1.7) para o modelo do PedeAí. Camada anticorrupção: o
 * formato do app não passa daqui. Itens e opções são casados pelo {@code externalCode} (código PDV).
 */
@Component
public class OpenDeliveryOrderMapper {
    private final ProductService productService;
    private final SectorService sectorService;
    private final PaymentMethodService paymentMethodService;

    public OpenDeliveryOrderMapper(ProductService productService, SectorService sectorService,
                                   PaymentMethodService paymentMethodService) {
        this.productService = productService;
        this.sectorService = sectorService;
        this.paymentMethodService = paymentMethodService;
    }

    public MarketplaceOrderRequest map(UUID storeId, OrderSource provider, String label, JsonNode order,
                                       boolean autoConfirm) {
        Map<String, ProductResponse> byCode = productService.list(storeId, null).stream()
                .filter(product -> product.code() != null)
                .collect(Collectors.toMap(ProductResponse::code, Function.identity(), (first, second) -> first));
        UUID defaultSector = sectorService.list(storeId).stream().filter(SectorResponse::defaultSector)
                .map(SectorResponse::id).findFirst().orElse(null);

        List<MarketplaceOrderRequest.Item> items = new ArrayList<>();
        for (JsonNode item : order.path("items")) {
            String code = text(item, "externalCode");
            ProductResponse product = code == null ? null : byCode.get(code);
            List<MarketplaceOrderRequest.Option> options = new ArrayList<>();
            for (JsonNode option : item.path("options")) {
                options.add(new MarketplaceOrderRequest.Option("Adicionais",
                        cut(Optional.ofNullable(text(option, "name")).orElse("Opção"), 80),
                        cut(text(option, "externalCode"), 40), quantity(option), cents(option.path("unitPrice"))));
            }
            String name = Optional.ofNullable(text(item, "name")).orElse("Item");
            int quantity = quantity(item);
            long unitPrice = cents(item.path("unitPrice"));
            long optionsPrice = item.has("optionsPrice") ? cents(item.path("optionsPrice"))
                    : options.stream().mapToLong(option -> option.unitPriceCents() * option.quantity()).sum();
            double exact = item.path("quantity").asDouble(1);
            if (exact != Math.rint(exact)) {
                // Venda por peso: entra como 1 unidade com o total do app, para o valor bater com o que o cliente pagou.
                name = name + " (" + item.path("quantity").asString() + " "
                        + Optional.ofNullable(text(item, "unit")).orElse("un").toLowerCase(Locale.ROOT) + ")";
                quantity = 1;
                unitPrice = cents(item.path("totalPrice")) - optionsPrice;
            }
            items.add(new MarketplaceOrderRequest.Item(product == null ? null : product.id(), cut(code, 40),
                    cut(name, 120), product == null || product.effectiveSectorId() == null ? defaultSector
                    : product.effectiveSectorId(), quantity, unitPrice, optionsPrice,
                    cut(text(item, "specialInstructions"), 300), options));
        }

        long merchantDiscount = 0;
        long platformSubsidy = 0;
        for (JsonNode discount : order.path("discounts")) {
            for (JsonNode sponsor : discount.path("sponsorshipValues")) {
                long value = cents(sponsor.path("amount"));
                if ("MERCHANT".equalsIgnoreCase(text(sponsor, "name"))) {
                    merchantDiscount += value;
                } else {
                    platformSubsidy += value;
                }
            }
        }
        long deliveryFee = 0;
        long additionalFees = 0;
        for (JsonNode fee : order.path("otherFees")) {
            if ("DELIVERY_FEE".equalsIgnoreCase(text(fee, "type"))) {
                deliveryFee += cents(fee.path("price"));
            } else {
                additionalFees += cents(fee.path("price"));
            }
        }

        JsonNode customer = order.path("customer");
        JsonNode address = order.path("delivery").path("deliveryAddress");
        String notes = Stream.of(text(order, "extraInfo"), text(order.path("delivery"), "pickupCode") == null ? null
                        : "Código de retirada: " + text(order.path("delivery"), "pickupCode"))
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" · "));

        return new MarketplaceOrderRequest(provider, text(order, "id"), cut(text(order, "displayId"), 40),
                "DELIVERY".equalsIgnoreCase(text(order, "type")) ? OrderType.DELIVERY : OrderType.TAKEOUT,
                scheduledFor(order), cut(text(customer, "name"), 120), cut(text(customer.path("phone"), "number"), 20),
                address.isMissingNode() || address.isNull() ? null : new DeliveryAddressResponse(
                        cut(Optional.ofNullable(text(address, "street")).orElse(text(address, "formattedAddress")),
                                120),
                        cut(Optional.ofNullable(text(address, "number")).orElse("s/n"), 20),
                        cut(text(address, "complement"), 80),
                        cut(Optional.ofNullable(text(address, "district")).orElse("-"), 80),
                        cut(text(address, "city"), 80), cut(text(address, "state"), 40),
                        cut(digits(text(address, "postalCode")), 10), cut(text(address, "reference"), 160)),
                cut(notes.isEmpty() ? null : notes, 500), items, merchantDiscount, platformSubsidy, deliveryFee,
                additionalFees, payments(storeId, label, order.path("payments")), autoConfirm);
    }

    /** Pago no app (PREPAID) vira "Online <app>" já pago; na entrega (PENDING), a forma equivalente, com troco. */
    private List<OrderPaymentRequest> payments(UUID storeId, String label, JsonNode payments) {
        List<PaymentMethodResponse> methods = paymentMethodService.list(storeId).stream()
                .filter(PaymentMethodResponse::active).toList();
        List<OrderPaymentRequest> result = new ArrayList<>();
        for (JsonNode payment : payments.path("methods")) {
            boolean prepaid = "PREPAID".equalsIgnoreCase(text(payment, "type"));
            long value = cents(payment.path("value"));
            Optional<PaymentMethodResponse> method = prepaid ? online(methods, label)
                    : methodOf(methods, text(payment, "method"));
            if (method.isEmpty() || value <= 0) {
                continue;
            }
            Long changeFor = null;
            if (!prepaid && method.get().type() == PaymentMethodType.CASH) {
                long change = cents(payment.path("changeFor"));
                changeFor = change > value ? change : null;
            }
            result.add(new OrderPaymentRequest(method.get().id(), value, changeFor, prepaid));
        }
        return result;
    }

    private static Optional<PaymentMethodResponse> online(List<PaymentMethodResponse> methods, String label) {
        List<PaymentMethodResponse> online = methods.stream()
                .filter(method -> method.type() == PaymentMethodType.ONLINE).toList();
        String wanted = label.toLowerCase(Locale.ROOT);
        return online.stream().filter(method -> method.name().toLowerCase(Locale.ROOT).contains(wanted)).findFirst()
                .or(() -> online.stream().findFirst());
    }

    private static Optional<PaymentMethodResponse> methodOf(List<PaymentMethodResponse> methods, String method) {
        PaymentMethodType type = switch (method == null ? "" : method.toUpperCase(Locale.ROOT)) {
            case "CASH" -> PaymentMethodType.CASH;
            case "CREDIT" -> PaymentMethodType.CREDIT;
            case "DEBIT", "CREDIT_DEBIT" -> PaymentMethodType.DEBIT;
            case "PIX" -> PaymentMethodType.PIX;
            case "MEAL_VOUCHER", "FOOD_VOUCHER" -> PaymentMethodType.VOUCHER;
            default -> PaymentMethodType.OTHER;
        };
        return methods.stream().filter(candidate -> candidate.type() == type).findFirst()
                .or(() -> methods.stream().filter(candidate -> candidate.type() == PaymentMethodType.OTHER)
                        .findFirst());
    }

    private static Instant scheduledFor(JsonNode order) {
        if (!"SCHEDULED".equalsIgnoreCase(text(order, "orderTiming"))) {
            return null;
        }
        try {
            String start = text(order.path("schedule"), "scheduledDateTimeStart");
            return start == null ? null : Instant.parse(start);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** {"value": 12.9, "currency": "BRL"} ou 12.9 viram 1290. */
    static long cents(JsonNode price) {
        JsonNode value = price.isObject() ? price.path("value") : price;
        if (value.isMissingNode() || value.isNull() || value.asString("").isBlank()) {
            return 0;
        }
        return new BigDecimal(value.asString()).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValue();
    }

    private static int quantity(JsonNode node) {
        return Math.max(1, (int) Math.round(node.path("quantity").asDouble(1)));
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asString(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String digits(String text) {
        return text == null ? null : text.replaceAll("\\D", "");
    }

    private static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
