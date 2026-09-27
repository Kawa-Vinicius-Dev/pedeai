package com.pedeai.integration.ifood;

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
 * Traduz o pedido do iFood para o modelo do PedeAí (camada anticorrupção, docs/05-integracoes.md#mapeamento-de-dados).
 * O formato do iFood não passa daqui.
 */
@Component
public class IfoodOrderMapper {
    private final ProductService productService;
    private final SectorService sectorService;
    private final PaymentMethodService paymentMethodService;

    public IfoodOrderMapper(ProductService productService, SectorService sectorService,
                            PaymentMethodService paymentMethodService) {
        this.productService = productService;
        this.sectorService = sectorService;
        this.paymentMethodService = paymentMethodService;
    }

    public MarketplaceOrderRequest map(UUID storeId, JsonNode order, boolean autoConfirm) {
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
                options.add(new MarketplaceOrderRequest.Option(
                        cut(Optional.ofNullable(text(option, "groupName")).orElse("Adicionais"), 80),
                        cut(Optional.ofNullable(text(option, "name")).orElse("Opção"), 80),
                        cut(text(option, "externalCode"), 40), quantity(option), cents(option.path("unitPrice"))));
            }
            items.add(new MarketplaceOrderRequest.Item(product == null ? null : product.id(), cut(code, 40),
                    cut(Optional.ofNullable(text(item, "name")).orElse("Item"), 120),
                    product == null || product.effectiveSectorId() == null ? defaultSector
                            : product.effectiveSectorId(),
                    quantity(item), cents(item.path("unitPrice")), cents(item.path("optionsPrice")),
                    cut(text(item, "observations"), 300), options));
        }

        long merchantDiscount = 0;
        long platformSubsidy = 0;
        for (JsonNode benefit : order.path("benefits")) {
            for (JsonNode sponsor : benefit.path("sponsorshipValues")) {
                long value = cents(sponsor.path("value"));
                if ("MERCHANT".equalsIgnoreCase(text(sponsor, "name"))) {
                    merchantDiscount += value;
                } else {
                    platformSubsidy += value;
                }
            }
        }

        JsonNode delivery = order.path("delivery");
        JsonNode address = delivery.path("deliveryAddress");
        JsonNode phone = order.path("customer").path("phone");
        String localizer = text(phone, "localizer");
        String notes = Stream.of(text(delivery, "observations"), text(order, "extraInfo"),
                        localizer == null ? null : "Localizador iFood: " + localizer)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" · "));

        return new MarketplaceOrderRequest(OrderSource.IFOOD, text(order, "id"), cut(text(order, "displayId"), 40),
                type(text(order, "orderType")), scheduledFor(order), cut(text(order.path("customer"), "name"), 120),
                cut(text(phone, "number"), 20),
                address.isMissingNode() || address.isNull() ? null : new DeliveryAddressResponse(
                        cut(Optional.ofNullable(text(address, "streetName")).orElse(text(address, "formattedAddress")),
                                120),
                        cut(Optional.ofNullable(text(address, "streetNumber")).orElse("s/n"), 20),
                        cut(text(address, "complement"), 80),
                        cut(Optional.ofNullable(text(address, "neighborhood")).orElse("-"), 80),
                        cut(text(address, "city"), 80), cut(text(address, "state"), 40),
                        cut(digits(text(address, "postalCode")), 10), cut(text(address, "reference"), 160)),
                cut(notes.isEmpty() ? null : notes, 500), items, merchantDiscount, platformSubsidy,
                cents(order.path("total").path("deliveryFee")), cents(order.path("total").path("additionalFees")),
                payments(storeId, order.path("payments")), autoConfirm);
    }

    /** Online vira pagamento já pago em "Online iFood"; na entrega, a forma equivalente da loja, com o troco. */
    private List<OrderPaymentRequest> payments(UUID storeId, JsonNode payments) {
        List<PaymentMethodResponse> methods = paymentMethodService.list(storeId).stream()
                .filter(PaymentMethodResponse::active).toList();
        List<OrderPaymentRequest> result = new ArrayList<>();
        for (JsonNode payment : payments.path("methods")) {
            boolean prepaid = payment.path("prepaid").asBoolean(false) || "ONLINE".equals(text(payment, "type"));
            long value = cents(payment.path("value"));
            Optional<PaymentMethodResponse> method = prepaid ? onlineIfood(methods)
                    : methodOf(methods, text(payment, "method"));
            if (method.isEmpty() || value <= 0) {
                continue;
            }
            Long changeFor = null;
            if (!prepaid && method.get().type() == PaymentMethodType.CASH) {
                long change = cents(payment.path("cash").path("changeFor"));
                changeFor = change > value ? change : null;
            }
            result.add(new OrderPaymentRequest(method.get().id(), value, changeFor, prepaid));
        }
        return result;
    }

    private static Optional<PaymentMethodResponse> onlineIfood(List<PaymentMethodResponse> methods) {
        List<PaymentMethodResponse> online = methods.stream()
                .filter(method -> method.type() == PaymentMethodType.ONLINE).toList();
        return online.stream().filter(method -> method.name().toLowerCase(Locale.ROOT).contains("ifood")).findFirst()
                .or(() -> online.stream().findFirst());
    }

    private static Optional<PaymentMethodResponse> methodOf(List<PaymentMethodResponse> methods, String ifoodMethod) {
        PaymentMethodType type = switch (ifoodMethod == null ? "" : ifoodMethod.toUpperCase(Locale.ROOT)) {
            case "CASH" -> PaymentMethodType.CASH;
            case "CREDIT" -> PaymentMethodType.CREDIT;
            case "DEBIT" -> PaymentMethodType.DEBIT;
            case "PIX" -> PaymentMethodType.PIX;
            case "MEAL_VOUCHER", "FOOD_VOUCHER" -> PaymentMethodType.VOUCHER;
            default -> PaymentMethodType.OTHER;
        };
        return methods.stream().filter(method -> method.type() == type).findFirst()
                .or(() -> methods.stream().filter(method -> method.type() == PaymentMethodType.OTHER).findFirst());
    }

    private static OrderType type(String orderType) {
        return switch (orderType == null ? "" : orderType) {
            case "TAKEOUT" -> OrderType.TAKEOUT;
            case "DINE_IN", "INDOOR" -> OrderType.DINE_IN;
            default -> OrderType.DELIVERY;
        };
    }

    private static Instant scheduledFor(JsonNode order) {
        if (!"SCHEDULED".equals(text(order, "orderTiming"))) {
            return null;
        }
        String start = text(order.path("schedule"), "deliveryDateTimeStart");
        return start == null ? null : Instant.parse(start);
    }

    /** A plataforma manda reais com decimais; o PedeAí guarda centavos. */
    static long cents(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return 0;
        }
        return new BigDecimal(value.asString()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Venda por peso ainda não existe: quantidade quebrada vira 1, sem zerar o item. */
    private static int quantity(JsonNode node) {
        return Math.max(1, (int) Math.round(node.path("quantity").asDouble(1)));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asString().isBlank() ? null : value.asString().trim();
    }

    private static String digits(String text) {
        return text == null ? null : text.replaceAll("\\D", "");
    }

    private static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
