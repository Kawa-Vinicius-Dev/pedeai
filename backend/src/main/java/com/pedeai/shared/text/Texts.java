package com.pedeai.shared.text;

import java.text.Normalizer;
import java.util.Locale;

public final class Texts {
    private Texts() {
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Chave para comparar nomes digitados de jeitos diferentes: "  São   José " e "sao jose" viram "sao jose". */
    public static String normalizeKey(String value) {
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
