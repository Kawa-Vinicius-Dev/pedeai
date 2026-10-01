package com.pedeai.integration.ifood;

import com.pedeai.catalog.dto.OptionGroupResponse;
import com.pedeai.catalog.dto.OptionItemResponse;
import com.pedeai.catalog.dto.ProductResponse;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O produto do PedeAí no formato de item do catálogo v2 do iFood: item (preço, status, categoria) → produto (nome,
 * descrição, foto) → grupos de complementos → complementos. Os ids são derivados dos ids daqui, então mandar de novo
 * atualiza o mesmo item. O {@code externalCode} é o código PDV (ou o id do produto, sem código), o mesmo que liga os
 * itens dos pedidos de volta.
 * ⚠️ Conferir o formato na homologação (docs/05-integracoes.md#sincronização-com-o-ifood).
 */
public final class IfoodItemPayload {
    private IfoodItemPayload() {
    }

    /**
     * Preço no iFood: o fixo do produto, ou o daqui com o acréscimo da loja, arredondado para cima até terminar em
     * ,90. Produto de preço zero (monte o seu) continua zero.
     */
    public static long productPrice(long cents, Long fixedCents, int markupBp) {
        if (fixedCents != null) {
            return fixedCents;
        }
        if (cents == 0 || markupBp == 0) {
            return cents;
        }
        long marked = Math.ceilDiv(cents * (10_000 + markupBp), 10_000);
        return Math.ceilDiv(marked + 10, 100) * 100 - 10;
    }

    /**
     * Complemento: o acréscimo arredondado para os 10 centavos de cima. Terminar em ,90 num adicional de R$ 2,00
     * quase dobraria o preço.
     */
    public static long optionPrice(long cents, int markupBp) {
        if (cents == 0 || markupBp == 0) {
            return cents;
        }
        return Math.ceilDiv(Math.ceilDiv(cents * (10_000 + markupBp), 10_000), 10) * 10;
    }

    public static Map<String, Object> build(ProductResponse product, String categoryId, List<OptionGroupResponse> groups,
                                            int markupBp, String imagePath) {
        String externalCode = product.code() == null ? product.id().toString() : product.code();
        boolean available = product.active() && product.available() && product.sellOnIfood();
        long price = productPrice(product.priceCents(), product.ifoodPriceCents(), markupBp);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id("item", product.id()));
        item.put("type", "DEFAULT");
        item.put("categoryId", categoryId);
        item.put("status", available ? "AVAILABLE" : "UNAVAILABLE");
        item.put("price", price(price));
        item.put("externalCode", externalCode);

        List<Map<String, Object>> products = new ArrayList<>();
        List<Map<String, Object>> optionGroups = new ArrayList<>();
        List<Map<String, Object>> options = new ArrayList<>();
        List<Map<String, Object>> productGroups = new ArrayList<>();
        for (OptionGroupResponse group : groups) {
            List<String> optionIds = new ArrayList<>();
            for (OptionItemResponse option : group.options()) {
                if (!option.active()) {
                    continue;
                }
                String optionProductId = id("option-product", option.id());
                Map<String, Object> optionProduct = new LinkedHashMap<>();
                optionProduct.put("id", optionProductId);
                optionProduct.put("externalCode", option.code() == null ? option.id().toString() : option.code());
                optionProduct.put("name", option.name());
                products.add(optionProduct);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", id("option", option.id()));
                entry.put("status", option.available() ? "AVAILABLE" : "UNAVAILABLE");
                entry.put("productId", optionProductId);
                entry.put("price", price(optionPrice(option.priceCents(), markupBp)));
                entry.put("externalCode", option.code() == null ? option.id().toString() : option.code());
                options.add(entry);
                optionIds.add(id("option", option.id()));
            }
            Map<String, Object> optionGroup = new LinkedHashMap<>();
            optionGroup.put("id", id("group", group.id()));
            optionGroup.put("name", group.name());
            optionGroup.put("status", "AVAILABLE");
            optionGroup.put("externalCode", group.id().toString());
            optionGroup.put("optionIds", optionIds);
            optionGroups.add(optionGroup);
            productGroups.add(Map.of("id", id("group", group.id()), "min", group.minChoices(),
                    "max", group.maxChoices()));
        }

        Map<String, Object> main = new LinkedHashMap<>();
        main.put("id", id("product", product.id()));
        main.put("externalCode", externalCode);
        main.put("name", product.name());
        main.put("description", product.description() == null ? "" : product.description());
        if (imagePath != null) {
            main.put("imagePath", imagePath);
        }
        main.put("optionGroups", productGroups);
        products.addFirst(main);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("item", item);
        body.put("products", products);
        body.put("optionGroups", optionGroups);
        body.put("options", options);
        return body;
    }

    private static Map<String, Object> price(long cents) {
        BigDecimal value = BigDecimal.valueOf(cents, 2);
        return Map.of("value", value, "originalValue", value);
    }

    /** Id estável no iFood a partir do id daqui: mandar de novo atualiza em vez de criar outro. */
    static String id(String kind, UUID localId) {
        return UUID.nameUUIDFromBytes((kind + ":" + localId).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
