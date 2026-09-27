package com.pedeai.printing.dto;

import com.pedeai.printing.domain.Codepage;
import com.pedeai.printing.domain.ConnectionType;
import com.pedeai.printing.domain.CutMode;
import com.pedeai.printing.domain.Printer;
import com.pedeai.printing.domain.PrinterStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** {@code status}: o último informado pelo agente. Se o agente está offline, a impressora também está. */
public record PrinterResponse(
        UUID id,
        UUID agentId,
        String name,
        ConnectionType connectionType,
        @Schema(types = {"string", "null"}) String host,
        @Schema(types = {"integer", "null"}) Integer port,
        @Schema(types = {"string", "null"}) String systemName,
        int paperWidthMm,
        int columns,
        Codepage codepage,
        CutMode cutMode,
        boolean active,
        PrinterStatus status,
        @Schema(types = {"string", "null"}) String statusDetail,
        @Schema(types = {"string", "null"}) Instant statusUpdatedAt
) {
    public static PrinterResponse from(Printer printer, boolean agentOnline) {
        PrinterStatus status = agentOnline ? printer.getStatus() : PrinterStatus.OFFLINE;
        String detail = agentOnline ? printer.getStatusDetail() : "Computador de impressão sem conexão.";
        return new PrinterResponse(printer.getId(), printer.getAgentId(), printer.getName(),
                printer.getConnectionType(), printer.getHost(), printer.getPort(), printer.getSystemName(),
                printer.getPaperWidthMm(), printer.getColumns(), printer.getCodepage(), printer.getCutMode(),
                printer.isActive(), status, detail, printer.getStatusUpdatedAt());
    }
}
