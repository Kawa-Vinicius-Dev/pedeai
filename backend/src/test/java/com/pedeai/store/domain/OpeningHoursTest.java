package com.pedeai.store.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpeningHoursTest {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    /** Segunda 11:00 às 15:00; sexta 18:00 às 02:00 (passa da meia-noite). */
    private static final List<OpeningHours> WEEK = List.of(
            new OpeningHours(1, LocalTime.of(11, 0), LocalTime.of(15, 0)),
            new OpeningHours(5, LocalTime.of(18, 0), LocalTime.of(2, 0)));

    @Test
    void sameDayPeriodOpensAtTheStartAndClosesAtTheEnd() {
        // 2026-10-05 é segunda.
        assertThat(open(LocalDateTime.of(2026, 10, 5, 10, 59))).isFalse();
        assertThat(open(LocalDateTime.of(2026, 10, 5, 11, 0))).isTrue();
        assertThat(open(LocalDateTime.of(2026, 10, 5, 14, 59))).isTrue();
        assertThat(open(LocalDateTime.of(2026, 10, 5, 15, 0))).isFalse();
        assertThat(open(LocalDateTime.of(2026, 10, 6, 12, 0))).isFalse();
    }

    @Test
    void overnightPeriodGoesIntoTheNextMorning() {
        // 2026-10-09 é sexta; 2026-10-10, sábado.
        assertThat(open(LocalDateTime.of(2026, 10, 9, 17, 59))).isFalse();
        assertThat(open(LocalDateTime.of(2026, 10, 9, 23, 30))).isTrue();
        assertThat(open(LocalDateTime.of(2026, 10, 10, 1, 59))).isTrue();
        assertThat(open(LocalDateTime.of(2026, 10, 10, 2, 0))).isFalse();
        assertThat(open(LocalDateTime.of(2026, 10, 10, 20, 0))).isFalse();
    }

    @Test
    void withoutHoursOnlyTheSwitchCounts() {
        assertThat(OpeningHours.isOpen(List.of(), SAO_PAULO,
                LocalDateTime.of(2026, 10, 5, 4, 0).atZone(SAO_PAULO).toInstant())).isTrue();
    }

    private static boolean open(LocalDateTime local) {
        return OpeningHours.isOpen(WEEK, SAO_PAULO, local.atZone(SAO_PAULO).toInstant());
    }
}
