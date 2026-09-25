package com.pedeai.payment.dto;

import com.pedeai.payment.domain.PaymentMethod;
import com.pedeai.payment.domain.PaymentMethodType;

import java.util.UUID;

public record PaymentMethodResponse(UUID id, String name, PaymentMethodType type, boolean active) {
    public static PaymentMethodResponse from(PaymentMethod method) {
        return new PaymentMethodResponse(method.getId(), method.getName(), method.getType(), method.isActive());
    }
}
