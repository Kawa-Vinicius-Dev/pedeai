package com.pedeai.payment.domain;

import java.util.List;

/** Formas de pagamento que toda loja ganha ao ser criada. Dá para renomear, desativar e criar outras. */
public final class DefaultPaymentMethods {
    public record Entry(String name, PaymentMethodType type) {
    }

    public static final List<Entry> ALL = List.of(
            new Entry("Dinheiro", PaymentMethodType.CASH),
            new Entry("Pix", PaymentMethodType.PIX),
            new Entry("Crédito", PaymentMethodType.CREDIT),
            new Entry("Débito", PaymentMethodType.DEBIT),
            new Entry("Vale-refeição", PaymentMethodType.VOUCHER),
            new Entry("Online iFood", PaymentMethodType.ONLINE),
            new Entry("Online 99Food", PaymentMethodType.ONLINE)
    );

    private DefaultPaymentMethods() {
    }
}
