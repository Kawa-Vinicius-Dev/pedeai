package com.pedeai.report.dto;

/**
 * Números de um período. Pedidos cancelados ficam fora de tudo, menos das duas linhas de cancelamento.
 * {@code grossCents} é o total cobrado dos clientes; taxa de entrega e taxa de serviço/adicional já estão dentro
 * dele e aparecem separadas para conferência.
 */
public record RevenueSummaryResponse(
        long orders,
        long grossCents,
        long subtotalCents,
        long discountCents,
        long platformSubsidyCents,
        long deliveryFeeCents,
        long additionalFeeCents,
        long averageTicketCents,
        long cancelledOrders,
        long cancelledCents
) {
}
