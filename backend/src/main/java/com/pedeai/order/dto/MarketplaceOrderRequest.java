package com.pedeai.order.dto;

import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pedido de marketplace já traduzido pelo adaptador (camada anticorrupção, docs/05-integracoes.md). Os preços são os
 * da plataforma, não os do cardápio: o cliente pagou o que o iFood mostrou. O cliente fica só no pedido, não vai
 * para a base de clientes da loja.
 *
 * @param discountCents        desconto pago pela loja
 * @param platformSubsidyCents desconto pago pela plataforma: a loja recebe esse valor no repasse
 */
public record MarketplaceOrderRequest(
        OrderSource source,
        String externalId,
        String displayId,
        OrderType type,
        Instant scheduledFor,
        String customerName,
        String customerPhone,
        DeliveryAddressResponse deliveryAddress,
        String notes,
        List<Item> items,
        long discountCents,
        long platformSubsidyCents,
        long deliveryFeeCents,
        long additionalFeeCents,
        List<OrderPaymentRequest> payments,
        boolean autoConfirm
) {
    /** {@code productId} nulo: item sem código PDV correspondente no cardápio ("não mapeado"). */
    public record Item(UUID productId, String code, String name, UUID sectorId, int quantity, long unitPriceCents,
                       long optionsPriceCents, String notes, List<Option> options) {
    }

    public record Option(String groupName, String name, String code, int quantity, long unitPriceCents) {
    }
}
