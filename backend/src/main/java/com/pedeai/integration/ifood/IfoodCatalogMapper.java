package com.pedeai.integration.ifood;

import com.pedeai.catalog.domain.PricingRule;
import com.pedeai.catalog.dto.ImportDraft;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cardápio do iFood para o formato do importador. Aceita os itens com nome e preço no próprio item ou apontando para
 * a lista de produtos da categoria ({@code productId}), porque a API de catálogo mostra os dois formatos.
 * ⚠️ Conferir na homologação (docs/05-integracoes.md#pendências-para-validar-na-documentação-oficial).
 */
public final class IfoodCatalogMapper {
    private IfoodCatalogMapper() {
    }

    public static ImportDraft toDraft(JsonNode categories) {
        List<ImportDraft.Category> result = new ArrayList<>();
        JsonNode list = categories.isArray() ? categories : categories.path("categories");
        for (JsonNode category : list) {
            Map<String, JsonNode> productsById = new HashMap<>();
            category.path("products").forEach(product -> productsById.put(product.path("id").asString(), product));
            boolean pizza = "PIZZA".equalsIgnoreCase(category.path("template").asString());
            List<ImportDraft.Product> products = new ArrayList<>();
            for (JsonNode item : category.path("items")) {
                JsonNode product = productsById.getOrDefault(item.path("productId").asString(), item);
                String name = text(item, product, "name");
                if (name == null) {
                    continue;
                }
                List<ImportDraft.Group> groups = new ArrayList<>();
                JsonNode optionGroups = item.has("optionGroups") ? item.path("optionGroups")
                        : product.path("optionGroups");
                for (JsonNode group : optionGroups) {
                    List<ImportDraft.Option> options = new ArrayList<>();
                    for (JsonNode option : group.path("options")) {
                        JsonNode optionProduct = productsById.getOrDefault(option.path("productId").asString(),
                                option);
                        String optionName = text(option, optionProduct, "name");
                        if (optionName != null) {
                            options.add(new ImportDraft.Option(optionName, cents(option.path("price")),
                                    blankToNull(text(option, optionProduct, "externalCode"))));
                        }
                    }
                    String groupName = group.path("name").asString("Adicionais");
                    int min = group.path("min").asInt(group.path("minimum").asInt(0));
                    int max = group.path("max").asInt(group.path("maximum").asInt(Math.max(1, options.size())));
                    boolean flavors = pizza && groupName.toLowerCase(Locale.ROOT).contains("sabor");
                    groups.add(new ImportDraft.Group(groupName, min, Math.max(max, Math.max(min, 1)),
                            flavors ? PricingRule.MAX : PricingRule.SUM, options));
                }
                products.add(new ImportDraft.Product("iFood: " + name, name, blankToNull(text(item, product,
                        "description")), cents(item.path("price")), blankToNull(text(item, product, "externalCode")),
                        !"UNAVAILABLE".equalsIgnoreCase(item.path("status").asString("AVAILABLE")), List.of(),
                        groups));
            }
            if (!products.isEmpty()) {
                result.add(new ImportDraft.Category(category.path("name").asString("Sem categoria"), products));
            }
        }
        return new ImportDraft(result);
    }

    /** {"value": 12.9} ou 12.9 viram 1290. */
    static long cents(JsonNode price) {
        JsonNode value = price.isObject() ? price.path("value") : price;
        if (value.isMissingNode() || value.isNull()) {
            return 0;
        }
        return new BigDecimal(value.asString()).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValue();
    }

    private static String text(JsonNode first, JsonNode second, String field) {
        String value = first.path(field).asString(null);
        if (value == null || value.isBlank()) {
            value = second.path(field).asString(null);
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
