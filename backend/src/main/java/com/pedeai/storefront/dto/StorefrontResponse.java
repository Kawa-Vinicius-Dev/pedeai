package com.pedeai.storefront.dto;

import com.pedeai.catalog.dto.OptionGroupResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Tudo o que o cardápio digital precisa numa ida só: a loja, se está recebendo pedidos, onde entrega, como dá para
 * pagar e o cardápio. Só o que é público: nada de setor, código interno ou item retirado do cardápio.
 */
public record StorefrontResponse(
        String name,
        String slug,
        @Schema(types = {"string", "null"}) String phone,
        boolean open,
        List<MenuDeliveryZoneResponse> deliveryZones,
        List<MenuPaymentMethodResponse> paymentMethods,
        List<MenuCategoryResponse> categories,
        List<OptionGroupResponse> optionGroups
) {
}
