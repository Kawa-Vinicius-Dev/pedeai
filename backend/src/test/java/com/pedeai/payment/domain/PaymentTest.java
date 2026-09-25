package com.pedeai.payment.domain;

import com.pedeai.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {
    private static final UUID STORE = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-25T19:00:00Z");
    private static final PaymentMethod CASH = new PaymentMethod(STORE, "Dinheiro", PaymentMethodType.CASH, 0, NOW);
    private static final PaymentMethod PIX = new PaymentMethod(STORE, "Pix", PaymentMethodType.PIX, 1, NOW);

    @Test
    void cashWithChangeKnowsHowMuchChangeToTake() {
        // R$ 72,00 com troco para R$ 100,00: o entregador leva R$ 28,00.
        Payment payment = new Payment(STORE, ORDER, CASH, 7200, 10_000L, false, USER, NOW);

        assertThat(payment.getChangeCents()).isEqualTo(2800);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaidAt()).isNull();
    }

    @Test
    void changeOnlyForCashAndNeverBelowTheAmount() {
        assertThatThrownBy(() -> new Payment(STORE, ORDER, PIX, 7200, 10_000L, true, USER, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(Payment.CHANGE_ONLY_CASH);
        assertThatThrownBy(() -> new Payment(STORE, ORDER, CASH, 7200, 5000L, false, USER, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(Payment.CHANGE_TOO_LOW);
    }

    @Test
    void paidOnCreationRecordsWhoAndWhen() {
        Payment payment = new Payment(STORE, ORDER, PIX, 7200, null, true, USER, NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> payment.markPaid(USER, NOW)).hasMessage(Payment.ALREADY_PAID);
    }

    @Test
    void cancelledPaymentStopsCounting() {
        Payment payment = new Payment(STORE, ORDER, CASH, 7200, null, false, USER, NOW);
        payment.cancel(NOW);

        assertThat(payment.counts()).isFalse();
        assertThatThrownBy(() -> payment.markPaid(USER, NOW)).hasMessage(Payment.ALREADY_CANCELLED);
    }
}
