package com.pedeai.integration.opendelivery;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * O evento Open Delivery ({@code eventId}, {@code eventType}, {@code orderId}) no formato do inbox, que é o mesmo do
 * iFood ({@code id}, {@code code}, {@code orderId}, {@code merchantId}). O evento da especificação não traz o merchant:
 * ele vem do polling (um merchant por chamada) ou do header {@code X-App-MerchantId} do webhook.
 */
public final class OpenDeliveryEvents {
    private OpenDeliveryEvents() {
    }

    public static ObjectNode toInbox(ObjectMapper json, JsonNode event, String merchantId) {
        ObjectNode normalized = json.createObjectNode();
        normalized.put("id", event.path("eventId").asString(event.path("id").asString()));
        normalized.put("code", event.path("eventType").asString(event.path("code").asString()));
        normalized.put("orderId", event.path("orderId").asString());
        normalized.put("merchantId", merchantId == null ? "" : merchantId);
        normalized.put("createdAt", event.path("createdAt").asString());
        if (event.has("orderURL")) {
            normalized.put("orderURL", event.path("orderURL").asString());
        }
        if (event.has("order")) {
            // Só o simulador manda o pedido junto com o evento.
            normalized.set("order", event.get("order"));
        }
        if (event.has("metadata")) {
            normalized.set("metadata", event.get("metadata"));
        }
        return normalized;
    }
}
