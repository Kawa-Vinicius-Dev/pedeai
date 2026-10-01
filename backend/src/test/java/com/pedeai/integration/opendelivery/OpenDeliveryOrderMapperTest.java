package com.pedeai.integration.opendelivery;

import com.pedeai.catalog.dto.ProductResponse;
import com.pedeai.catalog.dto.SectorResponse;
import com.pedeai.catalog.service.ProductService;
import com.pedeai.catalog.service.SectorService;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.MarketplaceOrderRequest;
import com.pedeai.payment.domain.PaymentMethodType;
import com.pedeai.payment.dto.PaymentMethodResponse;
import com.pedeai.payment.service.PaymentMethodService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static com.pedeai.support.TestSecurity.STORE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenDeliveryOrderMapperTest {
    private static final UUID BURGER = UUID.randomUUID();
    private static final UUID KITCHEN = UUID.randomUUID();
    private static final UUID CASH = UUID.randomUUID();
    private static final UUID ONLINE_99 = UUID.randomUUID();
    /** Pedido no formato da especificação Open Delivery v1.7 (schema Order), com o que importa para o PedeAí. */
    private static final String ORDER = """
            {"id":"od-1","type":"DELIVERY","displayId":"4821","createdAt":"2026-10-01T12:00:00Z",
             "orderTiming":"INSTANT","merchant":{"id":"m-1","name":"Lanchonete"},
             "items":[{"id":"i-1","name":"X-Burger","externalCode":"10","unit":"UN","quantity":2,
                       "specialInstructions":"sem cebola",
                       "unitPrice":{"value":29.9,"currency":"BRL"},"optionsPrice":{"value":4,"currency":"BRL"},
                       "totalPrice":{"value":67.8,"currency":"BRL"},
                       "options":[{"id":"o-1","name":"Bacon","externalCode":"B1","unit":"UN","quantity":1,
                                   "unitPrice":{"value":4,"currency":"BRL"},"totalPrice":{"value":4,"currency":"BRL"}}]},
                      {"id":"i-2","name":"Item sem código","externalCode":"","unit":"UN","quantity":1,
                       "unitPrice":{"value":5,"currency":"BRL"},"totalPrice":{"value":5,"currency":"BRL"}}],
             "otherFees":[{"name":"Entrega","type":"DELIVERY_FEE","receivedBy":"MERCHANT",
                           "price":{"value":8,"currency":"BRL"}},
                          {"name":"Serviço","type":"SERVICE_FEE","receivedBy":"MARKETPLACE",
                           "price":{"value":1.5,"currency":"BRL"}}],
             "discounts":[{"amount":{"value":10,"currency":"BRL"},"target":"CART","sponsorshipValues":[
                 {"name":"MERCHANT","amount":{"value":6,"currency":"BRL"}},
                 {"name":"MARKETPLACE","amount":{"value":4,"currency":"BRL"}}]}],
             "payments":{"prepaid":30,"pending":42.3,"methods":[
                 {"value":30,"currency":"BRL","type":"PREPAID","method":"CREDIT"},
                 {"value":42.3,"currency":"BRL","type":"PENDING","method":"CASH","changeFor":50}]},
             "customer":{"id":"c-1","name":"Bruna","phone":{"number":"11988887777"},"ordersCountOnMerchant":3},
             "delivery":{"deliveredBy":"MERCHANT","pickupCode":"7788","deliveryAddress":{"country":"BR","state":"SP",
                 "city":"São Paulo","district":"Moema","street":"Av. Ibirapuera","number":"500","complement":"ap 12",
                 "formattedAddress":"Av. Ibirapuera, 500","postalCode":"04029-000",
                 "coordinates":{"latitude":-23.6,"longitude":-46.66}}},
             "extraInfo":"Tocar o interfone"}""";

    @Test
    void mapsItemsFeesDiscountsPaymentsAndAddress() {
        ProductService products = mock(ProductService.class);
        when(products.list(STORE_ID, null)).thenReturn(List.of(new ProductResponse(BURGER, UUID.randomUUID(), "10",
                "X-Burger", null, 2990, null, KITCHEN, List.of(), true, true)));
        SectorService sectors = mock(SectorService.class);
        when(sectors.list(STORE_ID)).thenReturn(List.of(new SectorResponse(KITCHEN, "Cozinha", true, true)));
        PaymentMethodService methods = mock(PaymentMethodService.class);
        when(methods.list(STORE_ID)).thenReturn(List.of(
                new PaymentMethodResponse(CASH, "Dinheiro", PaymentMethodType.CASH, true),
                new PaymentMethodResponse(UUID.randomUUID(), "Online iFood", PaymentMethodType.ONLINE, true),
                new PaymentMethodResponse(ONLINE_99, "Online 99Food", PaymentMethodType.ONLINE, true)));

        MarketplaceOrderRequest order = new OpenDeliveryOrderMapper(products, sectors, methods).map(STORE_ID,
                OrderSource.NINETY_NINE_FOOD, "99Food", JsonMapper.builder().build().readTree(ORDER), false);

        assertThat(order.source()).isEqualTo(OrderSource.NINETY_NINE_FOOD);
        assertThat(order.externalId()).isEqualTo("od-1");
        assertThat(order.displayId()).isEqualTo("4821");
        assertThat(order.type()).isEqualTo(OrderType.DELIVERY);
        MarketplaceOrderRequest.Item burger = order.items().getFirst();
        assertThat(burger.productId()).isEqualTo(BURGER);
        assertThat(burger.sectorId()).isEqualTo(KITCHEN);
        assertThat(burger.quantity()).isEqualTo(2);
        assertThat(burger.unitPriceCents()).isEqualTo(2990);
        assertThat(burger.optionsPriceCents()).isEqualTo(400);
        assertThat(burger.notes()).isEqualTo("sem cebola");
        assertThat(burger.options()).extracting(MarketplaceOrderRequest.Option::name).containsExactly("Bacon");
        assertThat(order.items().get(1).productId()).isNull();
        assertThat(order.deliveryFeeCents()).isEqualTo(800);
        assertThat(order.additionalFeeCents()).isEqualTo(150);
        assertThat(order.discountCents()).isEqualTo(600);
        assertThat(order.platformSubsidyCents()).isEqualTo(400);
        assertThat(order.payments()).extracting(payment -> payment.paymentMethodId() + ":" + payment.amountCents()
                + ":" + payment.paid() + ":" + payment.changeForCents())
                .containsExactly(ONLINE_99 + ":3000:true:null", CASH + ":4230:false:5000");
        assertThat(order.customerName()).isEqualTo("Bruna");
        assertThat(order.deliveryAddress().neighborhood()).isEqualTo("Moema");
        assertThat(order.deliveryAddress().street()).isEqualTo("Av. Ibirapuera");
        assertThat(order.deliveryAddress().postalCode()).isEqualTo("04029000");
        assertThat(order.notes()).isEqualTo("Tocar o interfone · Código de retirada: 7788");
    }
}
