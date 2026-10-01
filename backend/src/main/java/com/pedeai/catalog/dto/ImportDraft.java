package com.pedeai.catalog.dto;

import com.pedeai.catalog.domain.PricingRule;

import java.util.List;

/**
 * Cardápio vindo de fora (planilha, iFood), no formato que o importador entende. {@code origin} diz de onde veio cada
 * produto ("linha 5", "iFood: X-Burger") para a mensagem de erro apontar o lugar certo.
 */
public record ImportDraft(List<Category> categories) {

    public record Category(String name, List<Product> products) {
    }

    /**
     * {@code groupNames}: grupos de adicionais que já existem na loja, pelo nome (planilha). {@code groups}: grupos
     * completos, criados se ainda não houver um com o mesmo nome (iFood).
     */
    public record Product(String origin, String name, String description, long priceCents, String code,
                          boolean available, List<String> groupNames, List<Group> groups) {
    }

    public record Group(String name, int minChoices, int maxChoices, PricingRule pricingRule, List<Option> options) {
    }

    public record Option(String name, long priceCents, String code) {
    }
}
