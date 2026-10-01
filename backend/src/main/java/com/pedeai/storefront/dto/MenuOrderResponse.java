package com.pedeai.storefront.dto;

/** {@code trackingCode}: o cliente acompanha o pedido em /loja/{slug}/pedido/{trackingCode}. */
public record MenuOrderResponse(int number, String trackingCode, long totalCents) {
}
