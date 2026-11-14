package com.pedeai.order.dto;

/**
 * Como o cliente está no programa de fidelidade. {@code ordersToNext}: quantos pedidos concluídos faltam para o
 * próximo prêmio. {@code rewardCents}: o desconto que ele pode usar agora (0 se não tem prêmio).
 */
public record LoyaltyStatusResponse(
        boolean enabled,
        long completedOrders,
        int ordersToNext,
        boolean rewardAvailable,
        long rewardCents
) {
    public static LoyaltyStatusResponse disabled() {
        return new LoyaltyStatusResponse(false, 0, 0, false, 0);
    }
}
