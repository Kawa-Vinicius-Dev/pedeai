package com.pedeai.customer.domain;

import com.pedeai.shared.exception.BusinessRuleException;

/** Telefone no formato E.164, que é a chave do cliente na loja. */
public final class Phones {
    static final String INVALID = "Telefone inválido. Informe DDD e número, como (11) 99999-0000.";

    private Phones() {
    }

    /**
     * "(11) 99999-0000" vira "+5511999990000". Sem "+" e com 10 ou 11 dígitos (DDD e número), assume Brasil.
     * Com "+", o código do país já veio digitado.
     */
    public static String normalize(String input) {
        String trimmed = input == null ? "" : input.trim();
        String digits = trimmed.replaceAll("\\D", "");
        boolean withCountryCode = trimmed.startsWith("+");
        if (!withCountryCode && (digits.length() == 10 || digits.length() == 11)) {
            digits = "55" + digits;
        }
        // E.164 tem até 15 dígitos. Sem "+", só aceitamos número brasileiro completo (55 + DDD + número).
        int minimum = withCountryCode ? 8 : 12;
        if (digits.length() < minimum || digits.length() > 15) {
            throw new BusinessRuleException(INVALID);
        }
        return "+" + digits;
    }
}
