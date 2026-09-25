package com.pedeai.catalog.domain;

import java.util.UUID;

/** Dados de uma opção vindos do formulário. {@code id} nulo cria uma opção nova. */
public record OptionDraft(UUID id, String code, String name, long priceCents, boolean available, boolean active) {
}
