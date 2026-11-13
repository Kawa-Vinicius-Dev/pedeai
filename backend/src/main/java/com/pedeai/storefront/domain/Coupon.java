package com.pedeai.storefront.domain;

import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/** Cupom de desconto do cardápio digital. O desconto é sobre os itens, nunca sobre a taxa de entrega. */
@Entity
@Table(name = "coupon")
public class Coupon {
    public static final String NOT_AVAILABLE = "Cupom inválido ou vencido.";
    public static final String MIN_ORDER = "Este cupom vale para pedidos a partir de %s.";
    public static final String PERCENT_RANGE = "A porcentagem deve ficar entre 1% e 100%.";
    private static final long FULL_BP = 10_000;

    @Id
    private UUID id;
    private UUID storeId;
    private String code;
    @Enumerated(EnumType.STRING)
    private CouponType type;
    private long amount;
    private long minOrderCents;
    private Integer maxUses;
    private int usedCount;
    private LocalDate validUntil;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected Coupon() {
    }

    public Coupon(UUID storeId, String code, CouponType type, long amount, long minOrderCents, Integer maxUses,
                  LocalDate validUntil, boolean active, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.createdAt = now;
        update(code, type, amount, minOrderCents, maxUses, validUntil, active, now);
    }

    public void update(String code, CouponType type, long amount, long minOrderCents, Integer maxUses,
                       LocalDate validUntil, boolean active, Instant now) {
        if (type == CouponType.PERCENT && amount > FULL_BP) {
            throw new BusinessRuleException(PERCENT_RANGE);
        }
        this.code = normalize(code);
        this.type = type;
        this.amount = amount;
        this.minOrderCents = minOrderCents;
        this.maxUses = maxUses;
        this.validUntil = validUntil;
        this.active = active;
        this.updatedAt = now;
    }

    /** "bemvindo 10" e "BEMVINDO10" são o mesmo cupom. */
    public static String normalize(String code) {
        return code == null ? "" : code.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /**
     * Quanto o cupom tira de um pedido com este valor de itens.
     *
     * @param today o dia de hoje no fuso da loja
     * @param formattedMinimum o pedido mínimo já em reais, para a mensagem
     */
    public long discountFor(long subtotalCents, LocalDate today, String formattedMinimum) {
        boolean expired = validUntil != null && today.isAfter(validUntil);
        boolean usedUp = maxUses != null && usedCount >= maxUses;
        if (!active || expired || usedUp) {
            throw new BusinessRuleException(NOT_AVAILABLE);
        }
        if (subtotalCents < minOrderCents) {
            throw new BusinessRuleException(MIN_ORDER.formatted(formattedMinimum));
        }
        long discount = type == CouponType.PERCENT ? subtotalCents * amount / FULL_BP : amount;
        return Math.min(discount, subtotalCents);
    }

    public void used() {
        this.usedCount++;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public CouponType getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public long getMinOrderCents() {
        return minOrderCents;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public boolean isActive() {
        return active;
    }
}
