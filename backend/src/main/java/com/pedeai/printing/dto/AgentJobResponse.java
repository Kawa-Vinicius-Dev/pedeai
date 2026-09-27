package com.pedeai.printing.dto;

import com.pedeai.printing.domain.DocumentType;
import com.pedeai.printing.domain.PrintJob;

import java.time.Instant;
import java.util.UUID;

/**
 * Trabalho para o agente imprimir. {@code payload}: bytes ESC/POS em base64, prontos para a impressora.
 * {@code deliveryKey}: o agente anota no diário e nunca imprime a mesma chave duas vezes.
 */
public record AgentJobResponse(UUID id, UUID printerId, String deliveryKey, DocumentType documentType,
                               byte[] payload, Instant createdAt) {
    public static AgentJobResponse from(PrintJob job) {
        return new AgentJobResponse(job.getId(), job.getPrinterId(), job.getDeliveryKey(), job.getDocumentType(),
                job.getPayload(), job.getCreatedAt());
    }
}
