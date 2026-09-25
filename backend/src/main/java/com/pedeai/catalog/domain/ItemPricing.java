package com.pedeai.catalog.domain;

import com.pedeai.shared.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Preço de um item montado (produto + opções escolhidas). Também valida as escolhas: mínimo e máximo de
 * cada grupo, opção pausada, opção que não pertence ao produto. É a mesma conta que o pedido vai usar.
 */
public final class ItemPricing {
    private ItemPricing() {
    }

    public record OptionChoice(UUID optionId, int quantity) {
    }

    public record PricedOption(UUID optionId, UUID groupId, String groupName, String name, String code,
                               int quantity, long unitPriceCents) {
    }

    public record PricedItem(long basePriceCents, long optionsPriceCents, long unitPriceCents, int quantity,
                             long totalCents, List<PricedOption> options) {
    }

    /**
     * @param groups grupos de adicionais do produto, na ordem em que aparecem para o cliente
     */
    public static PricedItem price(Product product, List<OptionGroup> groups, int quantity,
                                   List<OptionChoice> choices) {
        if (!product.isActive()) {
            throw new BusinessRuleException("Este produto não está mais à venda.");
        }
        if (!product.isAvailable()) {
            throw new BusinessRuleException("Produto pausado no momento: " + product.getName() + ".");
        }
        if (quantity < 1) {
            throw new BusinessRuleException("A quantidade deve ser pelo menos 1.");
        }
        Map<UUID, Integer> requested = requestedQuantities(choices);

        List<PricedOption> pricedOptions = new ArrayList<>();
        Set<UUID> matched = new HashSet<>();
        long optionsPriceCents = 0;
        for (OptionGroup group : groups) {
            if (!group.isActive()) {
                continue;
            }
            List<PricedOption> chosen = new ArrayList<>();
            int count = 0;
            for (OptionItem option : group.getOptions()) {
                Integer optionQuantity = requested.get(option.getId());
                if (optionQuantity == null) {
                    continue;
                }
                matched.add(option.getId());
                validateOption(group, option, optionQuantity);
                chosen.add(new PricedOption(option.getId(), group.getId(), group.getName(), option.getName(),
                        option.getCode(), optionQuantity, option.getPriceCents()));
                count += optionQuantity;
            }
            validateCount(group, count);
            optionsPriceCents += groupPriceCents(group.getPricingRule(), chosen);
            pricedOptions.addAll(chosen);
        }
        if (matched.size() != requested.size()) {
            throw new BusinessRuleException("Opção inválida para este produto.");
        }

        long unitPriceCents = product.getPriceCents() + optionsPriceCents;
        return new PricedItem(product.getPriceCents(), optionsPriceCents, unitPriceCents, quantity,
                Math.multiplyExact(unitPriceCents, quantity), List.copyOf(pricedOptions));
    }

    static long groupPriceCents(PricingRule rule, List<PricedOption> chosen) {
        if (chosen.isEmpty()) {
            return 0;
        }
        return switch (rule) {
            case SUM -> chosen.stream().mapToLong(option -> option.unitPriceCents() * option.quantity()).sum();
            case MAX -> chosen.stream().mapToLong(PricedOption::unitPriceCents).max().orElse(0);
            case AVERAGE -> BigDecimal.valueOf(chosen.stream().mapToLong(PricedOption::unitPriceCents).sum())
                    .divide(BigDecimal.valueOf(chosen.size()), 0, RoundingMode.HALF_UP)
                    .longValueExact();
        };
    }

    private static Map<UUID, Integer> requestedQuantities(List<OptionChoice> choices) {
        Map<UUID, Integer> requested = new LinkedHashMap<>();
        for (OptionChoice choice : choices) {
            if (choice.quantity() < 1) {
                throw new BusinessRuleException("A quantidade de cada opção deve ser pelo menos 1.");
            }
            if (requested.putIfAbsent(choice.optionId(), choice.quantity()) != null) {
                throw new BusinessRuleException("A mesma opção foi escolhida duas vezes.");
            }
        }
        return requested;
    }

    private static void validateOption(OptionGroup group, OptionItem option, int quantity) {
        if (!option.isActive()) {
            throw new BusinessRuleException("Opção inválida para este produto.");
        }
        if (!option.isAvailable()) {
            throw new BusinessRuleException("Opção indisponível no momento: " + option.getName() + ".");
        }
        if (group.getPricingRule() != PricingRule.SUM && quantity > 1) {
            throw new BusinessRuleException("Em " + group.getName() + ", cada opção só pode ser escolhida uma vez.");
        }
    }

    private static void validateCount(OptionGroup group, int count) {
        if (count < group.getMinChoices()) {
            throw new BusinessRuleException(
                    "Escolha pelo menos " + choices(group.getMinChoices()) + " em " + group.getName() + ".");
        }
        if (count > group.getMaxChoices()) {
            throw new BusinessRuleException(
                    "Escolha no máximo " + choices(group.getMaxChoices()) + " em " + group.getName() + ".");
        }
    }

    private static String choices(int count) {
        return count == 1 ? "1 opção" : count + " opções";
    }
}
