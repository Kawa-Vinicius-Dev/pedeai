package com.pedeai.payment.dto;

import com.pedeai.payment.domain.CashMovement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Sangria (WITHDRAWAL) ou suprimento (DEPOSIT). */
public record CashMovementRequest(
        @NotNull(message = "Escolha sangria ou suprimento.")
        CashMovement.Type type,

        @NotNull(message = "Informe o valor.")
        @Positive(message = "O valor deve ser maior que zero.")
        @Max(value = 100_000_000, message = "Valor alto demais.")
        Long amountCents,

        @NotBlank(message = "Informe o motivo.")
        @Size(max = 160, message = "O motivo pode ter até 160 caracteres.")
        String reason
) {
}
