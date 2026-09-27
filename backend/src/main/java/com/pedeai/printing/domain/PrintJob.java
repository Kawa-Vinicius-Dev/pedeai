package com.pedeai.printing.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Um trabalho da fila de impressão: bytes ESC/POS prontos para uma impressora. O ciclo de vida está em
 * docs/04-impressao.md#ciclo-de-vida-de-um-trabalho.
 */
@Entity
@Table(name = "print_job")
public class PrintJob {
    /** Reserva do agente: se ele não confirmar até lá, o trabalho volta para a fila. */
    public static final Duration LEASE = Duration.ofMinutes(2);
    public static final int MAX_ATTEMPTS = 5;
    /** Espera entre tentativas depois de erro: 5 s, 15 s, 30 s, 1 min. */
    private static final List<Duration> BACKOFF = List.of(Duration.ofSeconds(5), Duration.ofSeconds(15),
            Duration.ofSeconds(30), Duration.ofMinutes(1));

    public enum Status {
        PENDING, SENT, PRINTED, FAILED, UNCERTAIN, EXPIRED, CANCELLED
    }

    public enum Reason {
        AUTO, MANUAL, REPRINT, TEST
    }

    @Id
    private UUID id;
    private UUID storeId;
    private UUID printerId;
    private UUID agentId;
    @Enumerated(EnumType.STRING)
    private DocumentType documentType;
    private UUID orderId;
    private UUID sectorId;
    @Enumerated(EnumType.STRING)
    private Reason reason;
    private String idempotencyKey;
    private String deliveryKey;
    @Enumerated(EnumType.STRING)
    private Status status;
    private int attempts;
    private Instant nextAttemptAt;
    private Instant leaseUntil;
    private Instant expiresAt;
    private String lastError;
    private byte[] payload;
    private String preview;
    private Instant createdAt;
    private Instant sentAt;
    private Instant printedAt;
    @Version
    private Long version;

    protected PrintJob() {
    }

    public PrintJob(UUID storeId, UUID printerId, UUID agentId, DocumentType documentType, UUID orderId,
                    UUID sectorId, Reason reason, String idempotencyKey, byte[] payload, String preview,
                    Duration maxAge, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.printerId = printerId;
        this.agentId = agentId;
        this.documentType = documentType;
        this.orderId = orderId;
        this.sectorId = sectorId;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        // Chave de entrega própria: o diário do agente nunca imprime a mesma chave duas vezes.
        this.deliveryKey = UuidV7.generate().toString();
        this.status = Status.PENDING;
        this.nextAttemptAt = now;
        this.expiresAt = now.plus(maxAge);
        this.payload = payload;
        this.preview = preview;
        this.createdAt = now;
    }

    /** Agente reservou. Só um agente consegue: quem chega depois recebe falso. */
    public boolean reserve(Instant now) {
        if (status != Status.PENDING || nextAttemptAt.isAfter(now)) {
            return false;
        }
        status = Status.SENT;
        leaseUntil = now.plus(LEASE);
        sentAt = now;
        return true;
    }

    public void printed(Instant now) {
        requireSent();
        status = Status.PRINTED;
        printedAt = now;
        leaseUntil = null;
    }

    /** Erro ao mandar para a impressora: volta à fila com espera crescente, ou falha de vez na 5ª tentativa. */
    public void failed(String error, Instant now) {
        requireSent();
        lastError = error;
        retryOrFail(now);
    }

    /** O agente caiu entre "recebido" e "impresso": não dá para saber se saiu papel. Uma pessoa decide. */
    public void uncertain(String detail, Instant now) {
        requireSent();
        status = Status.UNCERTAIN;
        lastError = detail;
        leaseUntil = null;
    }

    /** Reserva venceu sem resposta: trata como erro transitório. */
    public boolean releaseExpiredLease(Instant now) {
        if (status != Status.SENT || leaseUntil == null || leaseUntil.isAfter(now)) {
            return false;
        }
        lastError = "O computador de impressão não confirmou a tempo.";
        retryOrFail(now);
        return true;
    }

    /** Velho demais para sair sozinho: sem isso, a impressora que volta depois de uma hora solta uma rajada. */
    public boolean expireIfStale(Instant now) {
        if (status != Status.PENDING || expiresAt.isAfter(now)) {
            return false;
        }
        status = Status.EXPIRED;
        return true;
    }

    public boolean cancelIfPending() {
        if (status != Status.PENDING) {
            return false;
        }
        status = Status.CANCELLED;
        return true;
    }

    private void retryOrFail(Instant now) {
        attempts++;
        leaseUntil = null;
        if (attempts >= MAX_ATTEMPTS) {
            status = Status.FAILED;
            return;
        }
        status = Status.PENDING;
        nextAttemptAt = now.plus(BACKOFF.get(Math.min(attempts, BACKOFF.size()) - 1));
    }

    private void requireSent() {
        if (status != Status.SENT) {
            throw new IllegalStateException("Trabalho " + id + " não está reservado (" + status + ").");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public UUID getPrinterId() {
        return printerId;
    }

    public UUID getAgentId() {
        return agentId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getSectorId() {
        return sectorId;
    }

    public Reason getReason() {
        return reason;
    }

    public String getDeliveryKey() {
        return deliveryKey;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public byte[] getPayload() {
        return payload;
    }

    public String getPreview() {
        return preview;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPrintedAt() {
        return printedAt;
    }
}
