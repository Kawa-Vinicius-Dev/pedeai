package com.pedeai.store.dto;

import com.pedeai.store.domain.OpeningHours;

import java.time.LocalTime;

/** {@code dayOfWeek}: 1 = segunda ... 7 = domingo. Fecha antes de abrir = passa da meia-noite. */
public record OpeningHoursResponse(int dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
    public static OpeningHoursResponse from(OpeningHours hours) {
        return new OpeningHoursResponse(hours.dayOfWeek(), hours.opensAt(), hours.closesAt());
    }
}
