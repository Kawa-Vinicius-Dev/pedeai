package com.pedeai.payment.dto;

import com.pedeai.payment.domain.Payment;
import com.pedeai.payment.domain.PaymentMethod;
import com.pedeai.payment.domain.PaymentMethodType;
import com.pedeai.payment.domain.PaymentOrigin;
import com.pedeai.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** {@code changeCents}: quanto de troco levar ("troco para R$ 100" num pagamento de R$ 72 são R$ 28). */
public record PaymentResponse(
        UUID id,
        UUID paymentMethodId,
        String methodName,
        PaymentMethodType methodType,
        long amountCents,
        @Schema(types = {"integer", "null"}, format = "int64") Long changeForCents,
        @Schema(types = {"integer", "null"}, format = "int64") Long changeCents,
        PaymentStatus status,
        PaymentOrigin origin,
        @Schema(types = {"string", "null"}) Instant paidAt,
        Instant createdAt
) {
    public static PaymentResponse from(Payment payment, PaymentMethod method) {
        return new PaymentResponse(payment.getId(), payment.getPaymentMethodId(), method.getName(), method.getType(),
                payment.getAmountCents(), payment.getChangeForCents(), payment.getChangeCents(), payment.getStatus(),
                payment.getOrigin(), payment.getPaidAt(), payment.getCreatedAt());
    }
}
