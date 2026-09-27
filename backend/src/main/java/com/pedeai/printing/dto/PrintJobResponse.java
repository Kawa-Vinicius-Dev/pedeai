package com.pedeai.printing.dto;

import com.pedeai.printing.domain.DocumentType;
import com.pedeai.printing.domain.PrintJob;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Situação de uma impressão, para a equipe. {@code preview}: o texto do ticket. */
public record PrintJobResponse(
        UUID id,
        @Schema(types = {"string", "null"}) UUID orderId,
        String title,
        UUID printerId,
        DocumentType documentType,
        @Schema(types = {"string", "null"}) UUID sectorId,
        PrintJob.Reason reason,
        PrintJob.Status status,
        int attempts,
        @Schema(types = {"string", "null"}) String lastError,
        String preview,
        Instant createdAt,
        @Schema(types = {"string", "null"}) Instant printedAt
) {
    public static PrintJobResponse from(PrintJob job) {
        return new PrintJobResponse(job.getId(), job.getOrderId(), job.getTitle(), job.getPrinterId(), job.getDocumentType(), job.getSectorId(),
                job.getReason(), job.getStatus(), job.getAttempts(), job.getLastError(), job.getPreview(),
                job.getCreatedAt(), job.getPrintedAt());
    }
}
