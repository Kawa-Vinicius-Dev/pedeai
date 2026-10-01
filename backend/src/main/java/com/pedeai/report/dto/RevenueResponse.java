package com.pedeai.report.dto;

import java.time.LocalDate;
import java.util.List;

/** Faturamento de {@code from} a {@code to} (dias operacionais, inclusive). */
public record RevenueResponse(
        LocalDate from,
        LocalDate to,
        RevenueSummaryResponse summary,
        List<RevenueGroupResponse> byDay,
        List<RevenueGroupResponse> bySource,
        List<RevenueGroupResponse> byType,
        List<PaymentMethodRevenueResponse> byPaymentMethod
) {
}
