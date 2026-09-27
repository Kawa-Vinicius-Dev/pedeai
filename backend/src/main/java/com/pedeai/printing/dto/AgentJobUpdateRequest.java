package com.pedeai.printing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code SENT}: o agente reservou (409 se outro chegou antes). {@code PRINTED}: saiu papel. {@code FAILED}: erro ao
 * mandar, volta para a fila. {@code UNCERTAIN}: o agente caiu no meio e não sabe se imprimiu.
 */
public record AgentJobUpdateRequest(
        @NotNull(message = "Informe o novo status.")
        Status status,

        @Size(max = 255, message = "O erro pode ter até 255 caracteres.")
        String error
) {
    public enum Status {
        SENT, PRINTED, FAILED, UNCERTAIN
    }
}
