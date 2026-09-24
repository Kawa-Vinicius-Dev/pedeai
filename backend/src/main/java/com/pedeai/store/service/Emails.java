package com.pedeai.store.service;

import java.util.Locale;

final class Emails {
    static final String ALREADY_IN_USE = "Este e-mail já está em uso.";

    private Emails() {
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
