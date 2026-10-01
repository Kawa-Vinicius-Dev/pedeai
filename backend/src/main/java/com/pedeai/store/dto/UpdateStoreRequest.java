package com.pedeai.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

/** Atualização parcial: campo ausente (null) fica como está. Texto vazio apaga documento e telefone. */
public record UpdateStoreRequest(
        @Pattern(regexp = ".*\\S.*", message = "O nome da loja não pode ficar em branco.")
        @Size(max = 120, message = "O nome da loja pode ter até 120 caracteres.")
        String name,

        @Size(max = 20, message = "O documento pode ter até 20 caracteres.")
        String document,

        @Size(max = 20, message = "O telefone pode ter até 20 caracteres.")
        String phone,

        @Size(max = 60, message = "Fuso horário inválido.")
        String timezone,

        LocalTime businessDayCutoff,

        @Min(value = 0, message = "A taxa de serviço não pode ser negativa.")
        @Max(value = 3000, message = "A taxa de serviço pode ser no máximo 30%.")
        Integer serviceFeeBp,

        Boolean autoConfirmOwnOrders,

        Boolean startPreparationOnConfirm,

        @Size(min = 3, max = 60, message = "O endereço do cardápio deve ter de 3 a 60 caracteres.")
        @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*",
                message = "Use só letras minúsculas, números e hífen no endereço do cardápio (ex.: pizzaria-bella).")
        String slug,

        Boolean menuAutoConfirm,

        /** Horário do cardápio, um período por dia. Lista vazia: sem restrição de horário. Nulo: não muda. */
        @Size(max = 7, message = "Informe no máximo um horário por dia da semana.")
        List<@Valid @NotNull OpeningHoursRequest> openingHours
) {
}
