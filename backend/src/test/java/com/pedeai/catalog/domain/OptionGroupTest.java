package com.pedeai.catalog.domain;

import com.pedeai.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionGroupTest {
    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    @Test
    void updateKeepsExistingOptionsAddsNewOnesAndRetiresTheOmitted() {
        OptionGroup group = new OptionGroup(UUID.randomUUID(), "Sabores", 1, 2, PricingRule.MAX, true, List.of(
                new OptionDraft(null, "10", "Calabresa", 4590, true, true),
                new OptionDraft(null, "11", "Atum", 4790, true, true)), NOW);
        UUID calabresa = group.getOptions().get(0).getId();
        UUID atum = group.getOptions().get(1).getId();

        group.update("Sabores", 1, 2, PricingRule.MAX, true, List.of(
                new OptionDraft(null, "12", "Portuguesa", 4990, true, true),
                new OptionDraft(calabresa, "10", "Calabresa", 4690, true, true)), NOW);

        assertThat(group.getOptions()).extracting(OptionItem::getName)
                .containsExactly("Portuguesa", "Calabresa", "Atum");
        assertThat(group.findOption(calabresa).orElseThrow().getPriceCents()).isEqualTo(4690);
        assertThat(group.findOption(atum).orElseThrow().isActive()).isFalse();
    }

    @Test
    void newOptionWithTheCodeOfARetiredOneBringsItBack() {
        OptionGroup group = new OptionGroup(UUID.randomUUID(), "Sabores", 1, 2, PricingRule.MAX, true, List.of(
                new OptionDraft(null, "10", "Calabresa", 4590, true, true),
                new OptionDraft(null, "11", "Atum", 4790, true, true)), NOW);
        UUID calabresa = group.getOptions().get(0).getId();
        UUID atum = group.getOptions().get(1).getId();
        group.update("Sabores", 1, 2, PricingRule.MAX, true,
                List.of(new OptionDraft(atum, "11", "Atum", 4790, true, true)), NOW);

        group.update("Sabores", 1, 2, PricingRule.MAX, true, List.of(
                new OptionDraft(atum, "11", "Atum", 4790, true, true),
                new OptionDraft(null, "10", "Calabresa especial", 4890, true, true)), NOW);

        // O código PDV é único no grupo: em vez de uma segunda "10", a opção antiga volta com os dados novos.
        assertThat(group.getOptions()).hasSize(2);
        OptionItem back = group.findOption(calabresa).orElseThrow();
        assertThat(back.isActive()).isTrue();
        assertThat(back.getName()).isEqualTo("Calabresa especial");
        assertThat(back.getPriceCents()).isEqualTo(4890);
        assertThat(group.getOptions()).extracting(OptionItem::getName).containsExactly("Atum", "Calabresa especial");
    }

    @Test
    void rejectsOptionFromAnotherGroup() {
        OptionGroup group = new OptionGroup(UUID.randomUUID(), "Sabores", 1, 2, PricingRule.MAX, true,
                List.of(new OptionDraft(null, null, "Calabresa", 4590, true, true)), NOW);

        assertThatThrownBy(() -> group.update("Sabores", 1, 2, PricingRule.MAX, true,
                List.of(new OptionDraft(UUID.randomUUID(), null, "Intrusa", 100, true, true)), NOW))
                .isInstanceOf(BusinessRuleException.class);
    }
}
