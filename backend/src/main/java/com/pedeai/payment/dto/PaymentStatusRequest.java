package com.pedeai.payment.dto;

import com.pedeai.payment.domain.PaymentStatus;
import jakarta.validation.constraints.NotNull;

/** {@code PAID}: recebeu o pagamento pendente. {@code CANCELLED}: o pagamento não vale mais. */
public record PaymentStatusRequest(
        @NotNull(message = "Informe o novo status do pagamento.")
        PaymentStatus status
) {
}
