package com.pedeai.printing.dto;

import com.pedeai.printing.domain.PrinterStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Heartbeat do agente, a cada 20 s, com o status de cada impressora dele. */
public record AgentStatusRequest(
        @Size(max = 40, message = "A versão pode ter até 40 caracteres.")
        String agentVersion,

        @NotNull(message = "Informe as impressoras.")
        List<@Valid PrinterStatusReport> printers
) {
    public record PrinterStatusReport(
            @NotNull(message = "Informe a impressora.")
            UUID printerId,

            @NotNull(message = "Informe o status.")
            PrinterStatus status,

            @Size(max = 255, message = "O detalhe pode ter até 255 caracteres.")
            String detail
    ) {
    }
}
