package com.pedeai.integration.service;

import com.pedeai.catalog.dto.ProductResponse;
import com.pedeai.catalog.service.ProductService;
import com.pedeai.integration.config.IfoodProperties;
import com.pedeai.integration.domain.MarketplaceConnection;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ForbiddenOperationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pedido de mentira do iFood, para desenvolver e demonstrar sem credenciais (docs/05-integracoes.md#desenvolvimento-
 * e-testes). Entra pelo inbox como um evento PLACED e segue o mesmo caminho do pedido real.
 */
@Service
public class SimulatorService {
    static final String DISABLED = "O simulador de pedidos do iFood está desligado neste servidor.";
    static final String NO_CODES = "Cadastre o código PDV em pelo menos um produto disponível para simular pedidos.";

    private final IfoodProperties properties;
    private final ConnectionService connections;
    private final ProductService productService;
    private final InboundService inbound;
    private final ObjectMapper json;
    private final Clock clock;

    public SimulatorService(IfoodProperties properties, ConnectionService connections, ProductService productService,
                            InboundService inbound, ObjectMapper json, Clock clock) {
        this.properties = properties;
        this.connections = connections;
        this.productService = productService;
        this.inbound = inbound;
        this.json = json;
        this.clock = clock;
    }

    /** @return o id do pedido no "iFood" */
    @Transactional
    public String simulateOrder(UUID storeId, UUID connectionId) {
        if (!properties.simulator()) {
            throw new ForbiddenOperationException(DISABLED);
        }
        MarketplaceConnection connection = connections.find(storeId, connectionId);
        List<ProductResponse> menu = productService.list(storeId, null).stream()
                .filter(product -> product.code() != null && product.active() && product.available()).toList();
        if (menu.isEmpty()) {
            throw new BusinessRuleException(NO_CODES);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String orderId = UUID.randomUUID().toString();
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (int index = 0; index < Math.min(2, menu.size()); index++) {
            ProductResponse product = menu.get(random.nextInt(menu.size()));
            int quantity = 1 + random.nextInt(2);
            BigDecimal unit = BigDecimal.valueOf(Math.max(product.priceCents(), 1000), 2);
            subtotal = subtotal.add(unit.multiply(BigDecimal.valueOf(quantity)));
            items.add(Map.of("index", index + 1, "name", product.name(), "externalCode", product.code(),
                    "quantity", quantity, "unitPrice", unit, "optionsPrice", 0, "totalPrice",
                    unit.multiply(BigDecimal.valueOf(quantity)), "options", List.of()));
        }
        BigDecimal deliveryFee = new BigDecimal("7.00");
        BigDecimal total = subtotal.add(deliveryFee);
        boolean cash = random.nextBoolean();
        Map<String, Object> payment = cash
                ? Map.of("method", "CASH", "type", "OFFLINE", "prepaid", false, "value", total,
                "cash", Map.of("changeFor", total.add(new BigDecimal("20.00"))))
                : Map.of("method", "CREDIT", "type", "ONLINE", "prepaid", true, "value", total);
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", orderId);
        order.put("displayId", String.valueOf(1000 + random.nextInt(9000)));
        order.put("orderType", "DELIVERY");
        order.put("orderTiming", "IMMEDIATE");
        order.put("createdAt", Instant.now(clock).toString());
        order.put("customer", Map.of("name", "Cliente iFood (simulado)",
                "phone", Map.of("number", "0800 000 0000", "localizer", "1234 5678")));
        order.put("delivery", Map.of("mode", "DEFAULT", "deliveredBy", "MERCHANT", "deliveryAddress", Map.of(
                "streetName", "Rua do Simulador", "streetNumber", "100", "neighborhood", "Centro",
                "city", "São Paulo", "state", "SP", "postalCode", "01000-000", "reference", "Pedido de teste")));
        order.put("items", items);
        order.put("total", Map.of("subTotal", subtotal, "deliveryFee", deliveryFee, "benefits", 0,
                "orderAmount", total, "additionalFees", 0));
        order.put("payments", Map.of("methods", List.of(payment)));
        inbound.record(OrderSource.IFOOD, json.valueToTree(Map.of(
                "id", "sim-plc-" + orderId, "code", "PLC", "fullCode", "PLACED", "orderId", orderId,
                "merchantId", connection.getExternalMerchantId(), "createdAt", Instant.now(clock).toString(),
                "order", order)));
        return orderId;
    }
}
