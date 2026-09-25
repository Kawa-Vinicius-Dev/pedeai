package com.pedeai.customer.domain;

import com.pedeai.shared.text.Texts;

/** Endereço digitado, já sem espaços sobrando. Campos opcionais vazios viram nulos. */
public record AddressDraft(String label, String street, String number, String complement, String neighborhood,
                           String city, String state, String postalCode, String reference) {
    public AddressDraft {
        label = Texts.trimToNull(label);
        street = street.trim();
        number = number.trim();
        complement = Texts.trimToNull(complement);
        neighborhood = neighborhood.trim();
        city = Texts.trimToNull(city);
        state = Texts.trimToNull(state);
        postalCode = Texts.trimToNull(postalCode);
        reference = Texts.trimToNull(reference);
    }

    /** Mesma rua, número e complemento, sem ligar para maiúsculas e acentos. */
    String samePlaceKey() {
        return Texts.normalizeKey(street) + "|" + Texts.normalizeKey(number) + "|"
                + (complement == null ? "" : Texts.normalizeKey(complement));
    }
}
