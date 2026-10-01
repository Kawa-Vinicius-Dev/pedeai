package com.pedeai.store.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @Test
    void nameBecomesALowercaseAddressWithoutAccents() {
        assertThat(Slugs.from("Pizzaria São João!")).isEqualTo("pizzaria-sao-joao");
        assertThat(Slugs.from("  Bar & Lanches  do Zé ")).isEqualTo("bar-lanches-do-ze");
        assertThat(Slugs.from("X")).isEqualTo("loja-x");
        assertThat(Slugs.from("!!!")).isEqualTo("loja");
        assertThat(Slugs.from("a".repeat(80))).hasSize(Slugs.MAX_LENGTH);
        assertThat(Slugs.from("a".repeat(49) + " b")).isEqualTo("a".repeat(49));
    }
}
