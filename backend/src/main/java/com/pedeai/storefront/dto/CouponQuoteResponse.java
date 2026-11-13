package com.pedeai.storefront.dto;

/** Quanto um cupom tira de um pedido, para o cliente ver antes de enviar. */
public record CouponQuoteResponse(String code, long discountCents) {
}
