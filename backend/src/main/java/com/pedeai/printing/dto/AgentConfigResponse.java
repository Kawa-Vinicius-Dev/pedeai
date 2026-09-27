package com.pedeai.printing.dto;

import com.pedeai.printing.domain.ConnectionType;
import com.pedeai.printing.domain.CutMode;
import com.pedeai.printing.domain.Printer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** O que o agente precisa para imprimir: as impressoras ativas dele, com a tabela de caracteres já resolvida. */
public record AgentConfigResponse(UUID agentId, String agentName, List<AgentPrinterResponse> printers) {

    /** {@code escPosCodepage}: o n do ESC t. {@code charset}: a tabela Java que codifica o texto. */
    public record AgentPrinterResponse(
            UUID id,
            String name,
            ConnectionType connectionType,
            @Schema(types = {"string", "null"}) String host,
            @Schema(types = {"integer", "null"}) Integer port,
            @Schema(types = {"string", "null"}) String systemName,
            int columns,
            int escPosCodepage,
            String charset,
            CutMode cutMode
    ) {
        public static AgentPrinterResponse from(Printer printer) {
            return new AgentPrinterResponse(printer.getId(), printer.getName(), printer.getConnectionType(),
                    printer.getHost(), printer.getPort(), printer.getSystemName(), printer.getColumns(),
                    printer.getCodepage().escPosNumber(), printer.getCodepage().charset(), printer.getCutMode());
        }
    }
}
