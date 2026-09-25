package com.pedeai.payment.domain;

import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Pagamento de um pedido. Pode ser dividido em várias formas. */
@Entity
@Table(name = "payment")
public class Payment {
    static final String CHANGE_ONLY_CASH = "Troco só existe em pagamento em dinheiro.";
    static final String CHANGE_TOO_LOW = "O troco deve ser para um valor maior ou igual ao valor pago.";
    static final String ALREADY_CANCELLED = "Este pagamento já foi cancelado.";
    static final String ALREADY_PAID = "Este pagamento já foi recebido.";

    @Id
    private UUID id;
    private UUID storeId;
    private UUID orderId;
    private UUID paymentMethodId;
    private long amountCents;
    private Long changeForCents;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    @Enumerated(EnumType.STRING)
    private PaymentOrigin origin;
    private Instant paidAt;
    private UUID receivedBy;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected Payment() {
    }

    public Payment(UUID storeId, UUID orderId, PaymentMethod method, long amountCents, Long changeForCents,
                   boolean paid, UUID userId, Instant now) {
        if (changeForCents != null) {
            if (method.getType() != PaymentMethodType.CASH) {
                throw new BusinessRuleException(CHANGE_ONLY_CASH);
            }
            if (changeForCents < amountCents) {
                throw new BusinessRuleException(CHANGE_TOO_LOW);
            }
        }
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.orderId = orderId;
        this.paymentMethodId = method.getId();
        this.amountCents = amountCents;
        this.changeForCents = changeForCents;
        this.status = PaymentStatus.PENDING;
        this.origin = PaymentOrigin.LOCAL;
        this.createdBy = userId;
        this.createdAt = now;
        this.updatedAt = now;
        if (paid) {
            markPaid(userId, now);
        }
    }

    public void markPaid(UUID userId, Instant now) {
        if (status == PaymentStatus.CANCELLED) {
            throw new BusinessRuleException(ALREADY_CANCELLED);
        }
        if (status == PaymentStatus.PAID) {
            throw new BusinessRuleException(ALREADY_PAID);
        }
        this.status = PaymentStatus.PAID;
        this.paidAt = now;
        this.receivedBy = userId;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        if (status == PaymentStatus.CANCELLED) {
            throw new BusinessRuleException(ALREADY_CANCELLED);
        }
        this.status = PaymentStatus.CANCELLED;
        this.updatedAt = now;
    }

    public boolean counts() {
        return status != PaymentStatus.CANCELLED;
    }

    /** Troco a levar: "troco para R$ 100" num pagamento de R$ 72 são R$ 28. */
    public Long getChangeCents() {
        return changeForCents == null ? null : changeForCents - amountCents;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public Long getChangeForCents() {
        return changeForCents;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentOrigin getOrigin() {
        return origin;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
