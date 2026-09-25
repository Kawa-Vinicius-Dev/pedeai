package com.pedeai.catalog.domain;

import com.pedeai.catalog.domain.ItemPricing.OptionChoice;
import com.pedeai.catalog.domain.ItemPricing.PricedItem;
import com.pedeai.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemPricingTest {
    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    private static final UUID STORE = UUID.randomUUID();

    private final OptionGroup flavors = group("Sabores", PricingRule.MAX, 1, 2,
            option("Calabresa", 4590), option("Quatro queijos", 5290), option("Marguerita", 3990));
    private final OptionGroup crust = group("Borda", PricingRule.SUM, 0, 1, option("Catupiry", 800));
    private final OptionGroup extras = group("Adicionais", PricingRule.SUM, 0, 3,
            option("Bacon", 400), option("Cheddar", 350));

    @Test
    void sumAddsEachOptionTimesItsQuantity() {
        Product burger = product(2500, extras);

        PricedItem item = ItemPricing.price(burger, List.of(extras), 2,
                List.of(choose(extras, "Bacon", 2), choose(extras, "Cheddar", 1)));

        assertThat(item.optionsPriceCents()).isEqualTo(1150);
        assertThat(item.unitPriceCents()).isEqualTo(3650);
        assertThat(item.totalCents()).isEqualTo(7300);
        assertThat(item.options()).extracting(ItemPricing.PricedOption::name).containsExactly("Bacon", "Cheddar");
    }

    @Test
    void maxChargesTheMostExpensiveFlavor() {
        Product pizza = product(0, flavors);

        PricedItem item = ItemPricing.price(pizza, List.of(flavors), 1,
                List.of(choose(flavors, "Calabresa"), choose(flavors, "Quatro queijos")));

        assertThat(item.unitPriceCents()).isEqualTo(5290);
    }

    @Test
    void averageChargesTheMeanOfTheFlavors() {
        OptionGroup average = group("Sabores", PricingRule.AVERAGE, 1, 3,
                option("Calabresa", 4590), option("Quatro queijos", 5290), option("Marguerita", 3990));
        Product pizza = product(0, average);

        PricedItem half = ItemPricing.price(pizza, List.of(average), 1,
                List.of(choose(average, "Calabresa"), choose(average, "Quatro queijos")));
        PricedItem thirds = ItemPricing.price(pizza, List.of(average), 1,
                List.of(choose(average, "Calabresa"), choose(average, "Quatro queijos"),
                        choose(average, "Marguerita")));

        assertThat(half.unitPriceCents()).isEqualTo(4940);
        // (45,90 + 52,90 + 39,90) / 3 = 46,2333... arredonda para 46,23
        assertThat(thirds.unitPriceCents()).isEqualTo(4623);
    }

    @Test
    void averageRoundsHalfCentsUp() {
        assertThat(ItemPricing.groupPriceCents(PricingRule.AVERAGE, List.of(priced(1001), priced(1002))))
                .isEqualTo(1002);
    }

    @Test
    void groupsAddUpOnTopOfTheBasePrice() {
        Product pizza = product(500, flavors, crust);

        PricedItem item = ItemPricing.price(pizza, List.of(flavors, crust), 1,
                List.of(choose(flavors, "Quatro queijos"), choose(crust, "Catupiry")));

        assertThat(item.basePriceCents()).isEqualTo(500);
        assertThat(item.optionsPriceCents()).isEqualTo(5290 + 800);
        assertThat(item.unitPriceCents()).isEqualTo(6590);
    }

    @Test
    void optionalGroupWithNothingChosenAddsNothing() {
        Product pizza = product(0, flavors, crust);

        PricedItem item = ItemPricing.price(pizza, List.of(flavors, crust), 1, List.of(choose(flavors, "Calabresa")));

        assertThat(item.unitPriceCents()).isEqualTo(4590);
    }

    @Test
    void requiresTheMinimumOfEachGroup() {
        Product pizza = product(0, flavors);

        assertThatThrownBy(() -> ItemPricing.price(pizza, List.of(flavors), 1, List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Escolha pelo menos 1 opção em Sabores.");
    }

    @Test
    void rejectsMoreThanTheMaximum() {
        Product pizza = product(0, flavors);

        assertThatThrownBy(() -> ItemPricing.price(pizza, List.of(flavors), 1, List.of(choose(flavors, "Calabresa"),
                choose(flavors, "Quatro queijos"), choose(flavors, "Marguerita"))))
                .hasMessage("Escolha no máximo 2 opções em Sabores.");
    }

    @Test
    void quantitiesCountTowardsTheMaximumInSumGroups() {
        Product burger = product(2500, extras);

        assertThatThrownBy(() -> ItemPricing.price(burger, List.of(extras), 1,
                List.of(choose(extras, "Bacon", 2), choose(extras, "Cheddar", 2))))
                .hasMessage("Escolha no máximo 3 opções em Adicionais.");
    }

    @Test
    void flavorsCannotBeRepeated() {
        Product pizza = product(0, flavors);

        assertThatThrownBy(() -> ItemPricing.price(pizza, List.of(flavors), 1, List.of(choose(flavors, "Calabresa", 2))))
                .hasMessage("Em Sabores, cada opção só pode ser escolhida uma vez.");
    }

    @Test
    void rejectsOptionThatDoesNotBelongToTheProduct() {
        Product pizza = product(0, flavors);

        assertThatThrownBy(() -> ItemPricing.price(pizza, List.of(flavors), 1,
                List.of(choose(flavors, "Calabresa"), new OptionChoice(UUID.randomUUID(), 1))))
                .hasMessage("Opção inválida para este produto.");
    }

    @Test
    void rejectsTheSameOptionTwice() {
        Product pizza = product(0, flavors);
        OptionChoice calabresa = choose(flavors, "Calabresa");

        assertThatThrownBy(() -> ItemPricing.price(pizza, List.of(flavors), 1, List.of(calabresa, calabresa)))
                .hasMessage("A mesma opção foi escolhida duas vezes.");
    }

    @Test
    void rejectsPausedOption() {
        Product pizza = product(0, flavors);
        find(flavors, "Calabresa").changeAvailability(false, NOW);

        assertThatThrownBy(() -> ItemPricing.price(pizza, List.of(flavors), 1, List.of(choose(flavors, "Calabresa"))))
                .hasMessage("Opção indisponível no momento: Calabresa.");
    }

    @Test
    void rejectsPausedOrRetiredProduct() {
        Product paused = product(1000);
        paused.changeAvailability(false, NOW);
        Product retired = new Product(STORE, draft(1000, List.of(), true, false), 0, NOW);

        assertThatThrownBy(() -> ItemPricing.price(paused, List.of(), 1, List.of()))
                .hasMessage("Produto pausado no momento: Produto.");
        assertThatThrownBy(() -> ItemPricing.price(retired, List.of(), 1, List.of()))
                .hasMessage("Este produto não está mais à venda.");
    }

    @Test
    void inactiveGroupsAreNotRequired() {
        OptionGroup inactive = group("Ponto da carne", PricingRule.SUM, 1, 1, option("Ao ponto", 0));
        inactive.update(inactive.getName(), 1, 1, PricingRule.SUM, false,
                List.of(new OptionDraft(inactive.getOptions().getFirst().getId(), null, "Ao ponto", 0, true, true)), NOW);
        Product burger = product(2500, inactive);

        assertThat(ItemPricing.price(burger, List.of(inactive), 1, List.of()).unitPriceCents()).isEqualTo(2500);
    }

    @Test
    void rejectsZeroQuantity() {
        assertThatThrownBy(() -> ItemPricing.price(product(1000), List.of(), 0, List.of()))
                .isInstanceOf(BusinessRuleException.class);
    }

    private static OptionGroup group(String name, PricingRule rule, int min, int max, OptionDraft... options) {
        return new OptionGroup(STORE, name, min, max, rule, true, Arrays.asList(options), NOW);
    }

    private static OptionDraft option(String name, long priceCents) {
        return new OptionDraft(null, null, name, priceCents, true, true);
    }

    private static Product product(long priceCents, OptionGroup... groups) {
        List<UUID> groupIds = Arrays.stream(groups).map(OptionGroup::getId).toList();
        return new Product(STORE, draft(priceCents, groupIds, true, true), 0, NOW);
    }

    private static ProductDraft draft(long priceCents, List<UUID> groupIds, boolean available, boolean active) {
        return new ProductDraft(UUID.randomUUID(), null, "Produto", null, priceCents, null, groupIds, available, active);
    }

    private static OptionChoice choose(OptionGroup group, String name) {
        return choose(group, name, 1);
    }

    private static OptionChoice choose(OptionGroup group, String name, int quantity) {
        return new OptionChoice(find(group, name).getId(), quantity);
    }

    private static OptionItem find(OptionGroup group, String name) {
        return group.getOptions().stream().filter(option -> option.getName().equals(name)).findFirst().orElseThrow();
    }

    private static ItemPricing.PricedOption priced(long priceCents) {
        return new ItemPricing.PricedOption(UUID.randomUUID(), UUID.randomUUID(), "G", "O", null, 1, priceCents);
    }
}
