package com.pedeai.printing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** O agente troca o código de pareamento pelo token. {@code name}: como o computador aparece na tela. */
public record AgentPairingRequest(
        @NotBlank(message = "Informe o código de pareamento.")
        @Pattern(regexp = "\\d{6}", message = "O código tem 6 dígitos.")
        String code,

        @NotBlank(message = "Informe o nome do computador.")
        @Size(max = 80, message = "O nome pode ter até 80 caracteres.")
        String name,

        @Size(max = 80, message = "O sistema pode ter até 80 caracteres.")
        String os,

        @Size(max = 40, message = "A versão pode ter até 40 caracteres.")
        String agentVersion
) {
}
