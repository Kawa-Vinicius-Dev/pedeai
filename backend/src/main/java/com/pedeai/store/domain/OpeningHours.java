package com.pedeai.store.domain;

import jakarta.persistence.Embeddable;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Horário do cardápio digital num dia da semana ({@code dayOfWeek}: 1 = segunda ... 7 = domingo). Fechar antes de
 * abrir passa da meia-noite: sexta 18:00 às 02:00 vale até as 2h de sábado.
 */
@Embeddable
public record OpeningHours(int dayOfWeek, LocalTime opensAt, LocalTime closesAt) {

    /** Sem horário cadastrado, não restringe: vale só a chave "Cardápio aberto". */
    public static boolean isOpen(List<OpeningHours> week, ZoneId zone, Instant now) {
        if (week.isEmpty()) {
            return true;
        }
        ZonedDateTime local = now.atZone(zone);
        LocalTime time = local.toLocalTime();
        DayOfWeek today = local.getDayOfWeek();
        DayOfWeek yesterday = today.minus(1);
        for (OpeningHours period : week) {
            boolean overnight = !period.closesAt().isAfter(period.opensAt());
            if (period.dayOfWeek() == today.getValue()) {
                boolean afterOpening = !time.isBefore(period.opensAt());
                if (overnight ? afterOpening : afterOpening && time.isBefore(period.closesAt())) {
                    return true;
                }
            }
            if (overnight && period.dayOfWeek() == yesterday.getValue() && time.isBefore(period.closesAt())) {
                return true;
            }
        }
        return false;
    }
}
