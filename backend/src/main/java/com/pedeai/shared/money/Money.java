package com.pedeai.shared.money;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Dinheiro trafega e fica gravado em centavos. Aqui só a formatação para mensagens ("R$ 1.234,56"). */
public final class Money {
    private static final Locale BRAZIL = Locale.of("pt", "BR");

    private Money() {
    }

    public static String format(long cents) {
        NumberFormat format = NumberFormat.getCurrencyInstance(BRAZIL);
        return format.format(BigDecimal.valueOf(cents, 2)).replace(' ', ' ');
    }

    /** Sem o "R$", para colunas de valor ("1.234,56"). */
    public static String plain(long cents) {
        NumberFormat format = NumberFormat.getNumberInstance(BRAZIL);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(BigDecimal.valueOf(cents, 2));
    }
}
