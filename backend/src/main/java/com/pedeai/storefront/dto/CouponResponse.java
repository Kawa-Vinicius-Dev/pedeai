package com.pedeai.storefront.dto;

import com.pedeai.storefront.domain.Coupon;
import com.pedeai.storefront.domain.CouponType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

public record CouponResponse(
        UUID id,
        String code,
        CouponType type,
        long amount,
        long minOrderCents,
        @Schema(types = {"integer", "null"}) Integer maxUses,
        int usedCount,
        @Schema(types = {"string", "null"}) LocalDate validUntil,
        boolean active
) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(coupon.getId(), coupon.getCode(), coupon.getType(), coupon.getAmount(),
                coupon.getMinOrderCents(), coupon.getMaxUses(), coupon.getUsedCount(), coupon.getValidUntil(),
                coupon.isActive());
    }
}
