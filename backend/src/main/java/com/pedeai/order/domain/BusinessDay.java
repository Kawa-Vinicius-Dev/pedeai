package com.pedeai.order.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Dia operacional: com virada às 05:00, um pedido à 01:30 ainda conta no dia anterior. */
public final class BusinessDay {
    private BusinessDay() {
    }

    public static LocalDate of(Instant instant, ZoneId zone, LocalTime cutoff) {
        ZonedDateTime local = instant.atZone(zone);
        LocalDate date = local.toLocalDate();
        return local.toLocalTime().isBefore(cutoff) ? date.minusDays(1) : date;
    }
}
