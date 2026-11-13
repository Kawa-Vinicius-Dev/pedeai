package com.pedeai.storefront.domain;

import com.pedeai.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z");

    @Test
    void percentIsOverTheItemsAndFixedNeverPassesThem() {
        assertThat(coupon(CouponType.PERCENT, 1000, 0, null, null).discountFor(6690, TODAY, "")).isEqualTo(669);
        assertThat(coupon(CouponType.FIXED, 500, 0, null, null).discountFor(6690, TODAY, "")).isEqualTo(500);
        assertThat(coupon(CouponType.FIXED, 5000, 0, null, null).discountFor(1400, TODAY, "")).isEqualTo(1400);
    }

    @Test
    void isValidThroughTheLastDayAndStopsAfterIt() {
        Coupon coupon = coupon(CouponType.FIXED, 500, 0, null, TODAY);

        assertThat(coupon.discountFor(2000, TODAY, "")).isEqualTo(500);
        assertThatThrownBy(() -> coupon.discountFor(2000, TODAY.plusDays(1), ""))
                .isInstanceOf(BusinessRuleException.class).hasMessage(Coupon.NOT_AVAILABLE);
    }

    @Test
    void stopsWhenTheUsesRunOutOrItIsInactive() {
        Coupon once = coupon(CouponType.FIXED, 500, 0, 1, null);
        once.used();

        assertThatThrownBy(() -> once.discountFor(2000, TODAY, "")).hasMessage(Coupon.NOT_AVAILABLE);
        Coupon inactive = new Coupon(UUID.randomUUID(), "OFF", CouponType.FIXED, 500, 0, null, null, false, NOW);
        assertThatThrownBy(() -> inactive.discountFor(2000, TODAY, "")).hasMessage(Coupon.NOT_AVAILABLE);
    }

    @Test
    void tellsTheMinimumOrderAndRejectsMoreThanAHundredPercent() {
        assertThatThrownBy(() -> coupon(CouponType.FIXED, 500, 5000, null, null).discountFor(4000, TODAY, "R$ 50,00"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Este cupom vale para pedidos a partir de R$ 50,00.");
        assertThatThrownBy(() -> coupon(CouponType.PERCENT, 10_001, 0, null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage(Coupon.PERCENT_RANGE);
    }

    private static Coupon coupon(CouponType type, long amount, long minOrder, Integer maxUses, LocalDate validUntil) {
        return new Coupon(UUID.randomUUID(), "bem vindo", type, amount, minOrder, maxUses, validUntil, true, NOW);
    }
}
