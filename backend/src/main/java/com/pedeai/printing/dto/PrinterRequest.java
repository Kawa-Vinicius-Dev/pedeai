package com.pedeai.printing.dto;

import com.pedeai.printing.domain.Codepage;
import com.pedeai.printing.domain.ConnectionType;
import com.pedeai.printing.domain.CutMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Rede: {@code host} e {@code port}. Sistema (USB pelo Windows): {@code systemName}. */
public record PrinterRequest(
        @NotNull(message = "Escolha o computador que controla a impressora.")
        UUID agentId,

        @NotBlank(message = "Informe o nome da impressora.")
        @Size(max = 60, message = "O nome pode ter até 60 caracteres.")
        String name,

        @NotNull(message = "Informe como a impressora está ligada.")
        ConnectionType connectionType,

        @Size(max = 255, message = "O endereço pode ter até 255 caracteres.")
        String host,

        @Min(value = 1, message = "Porta inválida.")
        @Max(value = 65535, message = "Porta inválida.")
        Integer port,

        @Size(max = 255, message = "O nome no Windows pode ter até 255 caracteres.")
        String systemName,

        @NotNull(message = "Informe a largura do papel.")
        Integer paperWidthMm,

        @NotNull(message = "Informe as colunas.")
        @Min(value = 24, message = "Use de 24 a 64 colunas.")
        @Max(value = 64, message = "Use de 24 a 64 colunas.")
        Integer columns,

        @NotNull(message = "Escolha a tabela de caracteres.")
        Codepage codepage,

        @NotNull(message = "Escolha o corte do papel.")
        CutMode cutMode,

        @NotNull(message = "Informe se a impressora está ativa.")
        Boolean active
) {
}
