package com.pedeai.store.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** {@code dayOfWeek}: 1 = segunda ... 7 = domingo. Fecha antes de abrir = passa da meia-noite. */
public record OpeningHoursRequest(
        @NotNull(message = "Informe o dia da semana.")
        @Min(value = 1, message = "Dia da semana inválido.")
        @Max(value = 7, message = "Dia da semana inválido.")
        Integer dayOfWeek,

        @NotNull(message = "Informe o horário de abertura.")
        LocalTime opensAt,

        @NotNull(message = "Informe o horário de fechamento.")
        LocalTime closesAt
) {
}
