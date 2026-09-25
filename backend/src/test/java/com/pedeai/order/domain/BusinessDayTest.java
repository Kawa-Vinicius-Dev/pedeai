package com.pedeai.order.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessDayTest {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalTime FIVE_AM = LocalTime.of(5, 0);

    @Test
    void orderAfterMidnightBelongsToThePreviousDay() {
        // 01:30 em São Paulo (04:30 UTC) do dia 26 ainda é o expediente do dia 25.
        assertThat(BusinessDay.of(Instant.parse("2026-09-26T04:30:00Z"), SAO_PAULO, FIVE_AM))
                .isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test
    void cutoffTimeStartsTheNewDay() {
        // 05:00 em ponto em São Paulo é 08:00 UTC.
        assertThat(BusinessDay.of(Instant.parse("2026-09-26T08:00:00Z"), SAO_PAULO, FIVE_AM))
                .isEqualTo(LocalDate.of(2026, 9, 26));
    }

    @Test
    void usesTheStoreTimeZoneNotUtc() {
        // 23:00 do dia 25 em São Paulo já é dia 26 em UTC, mas o expediente é do dia 25.
        assertThat(BusinessDay.of(Instant.parse("2026-09-26T02:00:00Z"), SAO_PAULO, FIVE_AM))
                .isEqualTo(LocalDate.of(2026, 9, 25));
    }
}
