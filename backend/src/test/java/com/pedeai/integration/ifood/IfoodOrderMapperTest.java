package com.pedeai.integration.ifood;

import com.pedeai.catalog.dto.ProductResponse;
import com.pedeai.catalog.dto.SectorResponse;
import com.pedeai.catalog.service.ProductService;
import com.pedeai.catalog.service.SectorService;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.MarketplaceOrderRequest;
import com.pedeai.order.dto.OrderPaymentRequest;
import com.pedeai.payment.domain.PaymentMethodType;
import com.pedeai.payment.dto.PaymentMethodResponse;
import com.pedeai.payment.service.PaymentMethodService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Payload no formato da documentação do iFood (detalhes do pedido), com os casos que mexem em dinheiro. */
class IfoodOrderMapperTest {
    private static final UUID STORE = UUID.randomUUID();
    private static final UUID KITCHEN = UUID.randomUUID();
    private static final UUID BAR = UUID.randomUUID();
    private static final UUID BURGER = UUID.randomUUID();
    private static final UUID CASH = UUID.randomUUID();
    private static final UUID IFOOD_ONLINE = UUID.randomUUID();
    private static final UUID OTHER_ONLINE = UUID.randomUUID();

    private static final String ORDER = """
            {"id":"b1b2c3","displayId":"7391","orderType":"DELIVERY","orderTiming":"SCHEDULED",
             "schedule":{"deliveryDateTimeStart":"2026-09-27T22:00:00Z"},
             "customer":{"name":"Maria S.","phone":{"number":"0800 000 0000","localizer":"1234 5678"}},
             "delivery":{"mode":"DEFAULT","deliveredBy":"MERCHANT","observations":"Portão verde",
               "deliveryAddress":{"streetName":"Rua das Flores","streetNumber":"123","complement":"ap 45",
                 "neighborhood":"Centro","city":"São Paulo","state":"SP","postalCode":"01234-567",
                 "reference":"portão azul"}},
             "items":[
               {"index":1,"name":"X-BURGER","externalCode":"10","quantity":2,"unitPrice":29.9,"optionsPrice":4.0,
                "totalPrice":67.8,"observations":"carne bem passada",
                "options":[{"name":"Bacon","externalCode":"B1","quantity":1,"unitPrice":4.0,"price":4.0}]},
               {"index":2,"name":"Suco da casa","externalCode":"999","quantity":1,"unitPrice":12.005,
                "optionsPrice":0,"totalPrice":12.0,"options":[]}],
             "benefits":[{"value":15.0,"target":"CART","sponsorshipValues":[
               {"name":"IFOOD","value":10.0},{"name":"MERCHANT","value":5.0}]}],
             "total":{"subTotal":79.8,"deliveryFee":7.0,"benefits":15.0,"additionalFees":0.99,"orderAmount":72.79},
             "payments":{"prepaid":0,"pending":72.79,"methods":[
               {"value":72.79,"currency":"BRL","method":"CASH","type":"OFFLINE","prepaid":false,
                "cash":{"changeFor":100.0}}]}}
            """;

    private final JsonMapper json = JsonMapper.builder().build();
    private IfoodOrderMapper mapper;

    @BeforeEach
    void setUp() {
        ProductService products = mock(ProductService.class);
        SectorService sectors = mock(SectorService.class);
        PaymentMethodService methods = mock(PaymentMethodService.class);
        when(products.list(STORE, null)).thenReturn(List.of(new ProductResponse(BURGER, UUID.randomUUID(), "10",
                "X-Burger", null, 2990, null, BAR, List.of(), true, true, true, null, null)));
        when(sectors.list(STORE)).thenReturn(List.of(new SectorResponse(BAR, "Bar", false, true),
                new SectorResponse(KITCHEN, "Cozinha", true, true)));
        when(methods.list(STORE)).thenReturn(List.of(
                new PaymentMethodResponse(CASH, "Dinheiro", PaymentMethodType.CASH, true),
                new PaymentMethodResponse(OTHER_ONLINE, "Online 99Food", PaymentMethodType.ONLINE, true),
                new PaymentMethodResponse(IFOOD_ONLINE, "Online iFood", PaymentMethodType.ONLINE, true)));
        mapper = new IfoodOrderMapper(products, sectors, methods);
    }

    @Test
    void mapsPricesDiscountsAddressAndCashWithChange() {
        MarketplaceOrderRequest order = mapper.map(STORE, json.readTree(ORDER), false);

        assertThat(order.externalId()).isEqualTo("b1b2c3");
        assertThat(order.displayId()).isEqualTo("7391");
        assertThat(order.type()).isEqualTo(OrderType.DELIVERY);
        assertThat(order.scheduledFor()).isEqualTo(Instant.parse("2026-09-27T22:00:00Z"));
        // Cupom de R$ 15: R$ 5 da loja viram desconto, R$ 10 do iFood viram subsídio.
        assertThat(order.discountCents()).isEqualTo(500);
        assertThat(order.platformSubsidyCents()).isEqualTo(1000);
        assertThat(order.deliveryFeeCents()).isEqualTo(700);
        assertThat(order.additionalFeeCents()).isEqualTo(99);
        assertThat(order.deliveryAddress().street()).isEqualTo("Rua das Flores");
        assertThat(order.deliveryAddress().postalCode()).isEqualTo("01234567");
        assertThat(order.notes()).isEqualTo("Portão verde · Localizador iFood: 1234 5678");
        assertThat(order.payments()).containsExactly(new OrderPaymentRequest(CASH, 7279L, 10000L, false));
    }

    @Test
    void itemsAreMatchedByPdvCodeAndUnmappedOnesGoToTheDefaultSector() {
        MarketplaceOrderRequest order = mapper.map(STORE, json.readTree(ORDER), true);

        MarketplaceOrderRequest.Item burger = order.items().get(0);
        assertThat(burger.productId()).isEqualTo(BURGER);
        assertThat(burger.sectorId()).isEqualTo(BAR);
        assertThat(burger.unitPriceCents()).isEqualTo(2990);
        assertThat(burger.optionsPriceCents()).isEqualTo(400);
        assertThat(burger.options()).singleElement().satisfies(option -> assertThat(option.name()).isEqualTo("Bacon"));

        MarketplaceOrderRequest.Item juice = order.items().get(1);
        assertThat(juice.productId()).isNull();
        assertThat(juice.sectorId()).isEqualTo(KITCHEN);
        // Meio centavo arredonda para cima, sem quebrar a conversão.
        assertThat(juice.unitPriceCents()).isEqualTo(1201);
        assertThat(order.autoConfirm()).isTrue();
    }

    @Test
    void weighedItemKeepsTheIfoodTotalSoThePaymentFits() {
        JsonNode weighed = json.readTree("""
                {"id":"w","displayId":"2","orderType":"DELIVERY","orderTiming":"IMMEDIATE","customer":{"name":"Ana"},
                 "items":[{"name":"Picanha","externalCode":"77","unit":"KG","quantity":2.4,"unitPrice":80.0,
                   "optionsPrice":0,"totalPrice":192.0,"options":[]}],
                 "total":{"deliveryFee":0},"payments":{"methods":[]}}""");

        MarketplaceOrderRequest.Item item = mapper.map(STORE, weighed, false).items().getFirst();

        assertThat(item.quantity()).isEqualTo(1);
        assertThat(item.unitPriceCents()).isEqualTo(19200);
        assertThat(item.name()).isEqualTo("Picanha (2.4 kg)");
    }

    @Test
    void onlinePaymentIsPaidInTheIfoodMethodAndTakeoutHasNoAddress() {
        JsonNode takeout = json.readTree("""
                {"id":"x","displayId":"1","orderType":"TAKEOUT","orderTiming":"IMMEDIATE","customer":{"name":"Ana"},
                 "items":[],"total":{"deliveryFee":0},"payments":{"methods":[
                   {"value":20.5,"method":"CREDIT","type":"ONLINE","prepaid":true}]}}""");

        MarketplaceOrderRequest order = mapper.map(STORE, takeout, false);

        assertThat(order.type()).isEqualTo(OrderType.TAKEOUT);
        assertThat(order.deliveryAddress()).isNull();
        assertThat(order.scheduledFor()).isNull();
        assertThat(order.payments()).containsExactly(new OrderPaymentRequest(IFOOD_ONLINE, 2050L, null, true));
    }
}
