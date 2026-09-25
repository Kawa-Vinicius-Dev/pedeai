package com.pedeai.payment.dto;

import com.pedeai.payment.domain.PaymentMethodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaymentMethodRequest(
        @NotBlank(message = "Informe o nome da forma de pagamento.")
        @Size(max = 60, message = "O nome pode ter até 60 caracteres.")
        String name,

        @NotNull(message = "Informe o tipo.")
        PaymentMethodType type,

        @NotNull(message = "Informe se a forma de pagamento está ativa.")
        Boolean active
) {
}
