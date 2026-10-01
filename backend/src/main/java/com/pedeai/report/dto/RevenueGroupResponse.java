package com.pedeai.report.dto;

/** Pedidos e total de um grupo: um dia, um canal (PEDEAI, IFOOD) ou um tipo (DELIVERY, TAKEOUT). */
public record RevenueGroupResponse(String key, long orders, long totalCents) {
}
