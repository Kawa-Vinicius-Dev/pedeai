package com.pedeai.store.domain;

import java.text.Normalizer;
import java.util.Locale;

/** "Pizzaria São João!" → "pizzaria-sao-joao": o endereço do cardápio digital. */
public final class Slugs {
    static final int MAX_LENGTH = 50;

    private Slugs() {
    }

    public static String from(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        if (slug.isEmpty()) {
            return "loja";
        }
        return slug.length() < 3 ? "loja-" + slug : slug;
    }
}
