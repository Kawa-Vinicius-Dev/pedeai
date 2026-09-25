package com.pedeai.order.domain;

import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.OptimisticLock;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * O pedido, de qualquer canal. Guarda cópia dos itens, do cliente e do endereço, e os totais. O status segue o
 * ciclo de vida de docs/01-fluxos.md: só anda para frente, pode pular etapas, e cancelar exige motivo.
 */
@Entity
@Table(name = "orders")
public class Order {
    static final String CANCELLED_IS_FINAL = "Este pedido foi cancelado e não muda mais de status.";
    static final String COMPLETED_IS_FINAL = "Este pedido já foi concluído.";
    static final String ONLY_FORWARD = "O status do pedido só anda para frente.";
    static final String DISPATCH_ONLY_DELIVERY = "Só pedido de delivery sai para entrega.";
    static final String DISCOUNT_TOO_HIGH = "O desconto não pode passar do valor dos itens.";

    @Id
    private UUID id;
    private UUID storeId;
    private LocalDate businessDate;
    private int number;
    @Enumerated(EnumType.STRING)
    private OrderType type;
    @Enumerated(EnumType.STRING)
    private OrderSource source;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    @Embedded
    private DeliveryAddress deliveryAddress;
    private String notes;
    private long subtotalCents;
    private long discountCents;
    private long platformSubsidyCents;
    private long deliveryFeeCents;
    private long additionalFeeCents;
    private long totalCents;
    private String externalId;
    private String externalDisplayId;
    private Instant scheduledFor;
    private Instant confirmedAt;
    private Instant preparationStartedAt;
    private Instant readyAt;
    private Instant dispatchedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private String cancelReason;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    // Os itens nascem com o pedido e nunca mudam de pedido: sem UPDATE da chave, e a lista não mexe na versão
    // (o status é que conta para "o pedido mudou em outra tela").
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    @OptimisticLock(excluded = true)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    /** Pedido lançado pela equipe (balcão, telefone, WhatsApp). Nasce recebido; a loja decide se confirma na hora. */
    public static Order placeOwn(UUID storeId, LocalDate businessDate, int number, OrderType type,
                                 OrderCustomer customer, DeliveryAddress deliveryAddress, String notes,
                                 List<OrderItem> items, long discountCents, long deliveryFeeCents, UUID createdBy,
                                 Instant now) {
        Order order = new Order();
        order.id = UuidV7.generate();
        order.storeId = storeId;
        order.businessDate = businessDate;
        order.number = number;
        order.type = type;
        order.source = OrderSource.PEDEAI;
        order.status = OrderStatus.RECEIVED;
        if (customer != null) {
            order.customerId = customer.id();
            order.customerName = customer.name();
            order.customerPhone = customer.phone();
        }
        order.deliveryAddress = deliveryAddress;
        order.notes = notes;
        order.items.addAll(items);
        order.createdBy = createdBy;
        order.createdAt = now;
        order.updatedAt = now;
        order.applyTotals(discountCents, deliveryFeeCents);
        return order;
    }

    private void applyTotals(long discountCents, long deliveryFeeCents) {
        long subtotal = items.stream().filter(OrderItem::isActive).mapToLong(OrderItem::getTotalCents).sum();
        if (discountCents > subtotal) {
            throw new BusinessRuleException(DISCOUNT_TOO_HIGH);
        }
        this.subtotalCents = subtotal;
        this.discountCents = discountCents;
        this.platformSubsidyCents = 0;
        this.deliveryFeeCents = deliveryFeeCents;
        this.additionalFeeCents = 0;
        this.totalCents = subtotal - discountCents + deliveryFeeCents;
    }

    /**
     * Avança o status. Aplicar o status que o pedido já tem não faz nada (marketplaces reenviam eventos).
     *
     * @return {@code false} se o pedido já estava nesse status
     */
    public boolean advanceTo(OrderStatus target, Instant now) {
        if (target == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Cancelamento tem motivo: use cancel.");
        }
        if (target == status) {
            return false;
        }
        if (status == OrderStatus.CANCELLED) {
            throw new BusinessRuleException(CANCELLED_IS_FINAL);
        }
        if (status == OrderStatus.COMPLETED) {
            throw new BusinessRuleException(COMPLETED_IS_FINAL);
        }
        if (target.ordinal() < status.ordinal()) {
            throw new BusinessRuleException(ONLY_FORWARD);
        }
        if (target == OrderStatus.DISPATCHED && type != OrderType.DELIVERY) {
            throw new BusinessRuleException(DISPATCH_ONLY_DELIVERY);
        }
        this.status = target;
        stamp(target, now);
        this.updatedAt = now;
        return true;
    }

    /** @return {@code false} se o pedido já estava cancelado */
    public boolean cancel(String reason, Instant now) {
        if (status == OrderStatus.CANCELLED) {
            return false;
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = now;
        this.cancelReason = reason;
        this.updatedAt = now;
        return true;
    }

    private void stamp(OrderStatus target, Instant now) {
        switch (target) {
            case CONFIRMED -> confirmedAt = now;
            case IN_PREPARATION -> preparationStartedAt = now;
            case READY -> readyAt = now;
            case DISPATCHED -> dispatchedAt = now;
            case COMPLETED -> completedAt = now;
            default -> {
            }
        }
    }

    public List<OrderItem> getActiveItems() {
        return items.stream().filter(OrderItem::isActive).toList();
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public int getNumber() {
        return number;
    }

    public OrderType getType() {
        return type;
    }

    public OrderSource getSource() {
        return source;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public DeliveryAddress getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getNotes() {
        return notes;
    }

    public long getSubtotalCents() {
        return subtotalCents;
    }

    public long getDiscountCents() {
        return discountCents;
    }

    public long getPlatformSubsidyCents() {
        return platformSubsidyCents;
    }

    public long getDeliveryFeeCents() {
        return deliveryFeeCents;
    }

    public long getAdditionalFeeCents() {
        return additionalFeeCents;
    }

    public long getTotalCents() {
        return totalCents;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getPreparationStartedAt() {
        return preparationStartedAt;
    }

    public Instant getReadyAt() {
        return readyAt;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** 0 antes de gravar; o JPA soma 1 a cada alteração gravada. */
    public long getVersion() {
        return version == null ? 0 : version;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
