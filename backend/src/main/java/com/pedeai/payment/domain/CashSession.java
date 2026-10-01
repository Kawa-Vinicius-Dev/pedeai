package com.pedeai.payment.domain;

import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Um turno do caixa: da abertura com o troco inicial ao fechamento com a contagem. Os pagamentos recebidos na loja
 * entre a abertura e o fechamento são os deste caixa (um caixa por loja no MVP).
 */
@Entity
@Table(name = "cash_session")
public class CashSession {
    public enum Status { OPEN, CLOSED }

    static final String ALREADY_CLOSED = "Este caixa já foi fechado.";

    /** Conferência de uma forma de pagamento no fechamento. */
    @Embeddable
    public record Count(UUID paymentMethodId, long expectedCents, long countedCents) {
    }

    @Id
    private UUID id;
    private UUID storeId;
    @Enumerated(EnumType.STRING)
    private Status status;
    private UUID openStoreId;
    private UUID openedBy;
    private Instant openedAt;
    private long openingAmountCents;
    private UUID closedBy;
    private Instant closedAt;
    private String notes;
    @ElementCollection
    @CollectionTable(name = "cash_session_count", joinColumns = @JoinColumn(name = "cash_session_id"))
    private List<Count> counts = new ArrayList<>();
    @Version
    private Long version;

    protected CashSession() {
    }

    public CashSession(UUID storeId, long openingAmountCents, UUID userId, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.status = Status.OPEN;
        this.openStoreId = storeId;
        this.openedBy = userId;
        this.openedAt = now;
        this.openingAmountCents = openingAmountCents;
    }

    public void requireOpen() {
        if (status != Status.OPEN) {
            throw new BusinessRuleException(ALREADY_CLOSED);
        }
    }

    public void close(List<Count> counts, String notes, UUID userId, Instant now) {
        requireOpen();
        this.counts = new ArrayList<>(counts);
        this.notes = notes;
        this.status = Status.CLOSED;
        this.openStoreId = null;
        this.closedBy = userId;
        this.closedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getOpenedBy() {
        return openedBy;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public long getOpeningAmountCents() {
        return openingAmountCents;
    }

    public UUID getClosedBy() {
        return closedBy;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getNotes() {
        return notes;
    }

    public List<Count> getCounts() {
        return List.copyOf(counts);
    }

    public Long getVersion() {
        return version;
    }
}
