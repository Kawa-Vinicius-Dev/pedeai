package com.pedeai.order.service;

import java.security.SecureRandom;
import java.util.Base64;

/** Código que o cliente usa para acompanhar o pedido: 128 bits aleatórios, impossível de adivinhar. */
final class TrackingCodes {
    private static final SecureRandom RANDOM = new SecureRandom();

    private TrackingCodes() {
    }

    static String next() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
