package com.pedeai.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * O dia operacional num relance. {@code averagePreparationSeconds}: da confirmação ao pronto, só dos pedidos que
 * ficaram prontos. {@code ordersByHour}: 24 posições, pela hora local em que o pedido entrou.
 */
public record DashboardResponse(
        LocalDate date,
        RevenueSummaryResponse summary,
        @Schema(types = {"integer", "null"}) Long averagePreparationSeconds,
        List<Integer> ordersByHour,
        List<TopProductResponse> topProducts,
        List<RevenueGroupResponse> bySource
) {
}
