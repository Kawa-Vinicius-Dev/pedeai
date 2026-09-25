package com.pedeai.catalog.dto;

import com.pedeai.catalog.domain.OptionGroup;
import com.pedeai.catalog.domain.PricingRule;

import java.util.List;
import java.util.UUID;

/** Traz também as opções retiradas ({@code active = false}): a tela de edição filtra. */
public record OptionGroupResponse(
        UUID id,
        String name,
        int minChoices,
        int maxChoices,
        PricingRule pricingRule,
        boolean active,
        List<OptionItemResponse> options
) {
    public static OptionGroupResponse from(OptionGroup group) {
        return new OptionGroupResponse(group.getId(), group.getName(), group.getMinChoices(), group.getMaxChoices(),
                group.getPricingRule(), group.isActive(),
                group.getOptions().stream().map(OptionItemResponse::from).toList());
    }
}
