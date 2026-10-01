package com.pedeai.integration.ifood;

import com.pedeai.catalog.domain.PricingRule;
import com.pedeai.catalog.dto.OptionGroupResponse;
import com.pedeai.catalog.dto.OptionItemResponse;
import com.pedeai.catalog.dto.ProductResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IfoodItemPayloadTest {

    @Test
    void productPriceGetsTheMarkupAndEndsIn90() {
        assertThat(IfoodItemPayload.productPrice(2990, null, 1500)).isEqualTo(3490); // 34,385 → 34,90
        assertThat(IfoodItemPayload.productPrice(1000, null, 1000)).isEqualTo(1190); // 11,00 → 11,90
        assertThat(IfoodItemPayload.productPrice(990, null, 1000)).isEqualTo(1090); // 10,89 → 10,90
        assertThat(IfoodItemPayload.productPrice(2990, 3200L, 1500)).isEqualTo(3200); // preço fixo manda
        assertThat(IfoodItemPayload.productPrice(2990, null, 0)).isEqualTo(2990); // sem acréscimo, igual
        assertThat(IfoodItemPayload.productPrice(0, null, 1500)).isZero(); // monte o seu continua zero
    }

    @Test
    void optionPriceRoundsUpToTheNext10Cents() {
        assertThat(IfoodItemPayload.optionPrice(200, 1500)).isEqualTo(230);
        assertThat(IfoodItemPayload.optionPrice(250, 1500)).isEqualTo(290); // 2,875 → 2,90
        assertThat(IfoodItemPayload.optionPrice(0, 1500)).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsTheItemWithStableIdsAndTheMarkedUpPrices() {
        UUID productId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID baconId = UUID.randomUUID();
        ProductResponse product = new ProductResponse(productId, UUID.randomUUID(), null, "X-Burger", null, 2990, null,
                null, List.of(groupId), true, true, false, null, null);
        OptionGroupResponse group = new OptionGroupResponse(groupId, "Adicionais", 0, 3, PricingRule.SUM, true, List.of(
                new OptionItemResponse(baconId, "B1", "Bacon", 400, true, true),
                new OptionItemResponse(UUID.randomUUID(), null, "Retirado", 100, true, false)));

        Map<String, Object> body = IfoodItemPayload.build(product, "cat-1", List.of(group), 1500, null);

        Map<String, Object> item = (Map<String, Object>) body.get("item");
        assertThat(item.get("externalCode")).isEqualTo(productId.toString());
        assertThat(item.get("status")).isEqualTo("UNAVAILABLE"); // "Vender no iFood" desligado
        assertThat(((Map<String, Object>) item.get("price")).get("value")).isEqualTo(new BigDecimal("34.90"));
        assertThat(item.get("id")).isEqualTo(IfoodItemPayload.id("item", productId)); // mandar de novo atualiza
        List<Map<String, Object>> options = (List<Map<String, Object>>) body.get("options");
        assertThat(options).hasSize(1);
        assertThat(options.getFirst().get("externalCode")).isEqualTo("B1");
        assertThat(((Map<String, Object>) options.getFirst().get("price")).get("value"))
                .isEqualTo(new BigDecimal("4.60"));
    }
}
