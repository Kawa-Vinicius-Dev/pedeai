package com.pedeai.catalog.dto;

import com.pedeai.catalog.domain.PricingRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Substitui o grupo inteiro. Opções que não vierem na lista são retiradas (ficam inativas, porque
 * pedidos antigos continuam apontando para elas).
 */
public record OptionGroupRequest(
        @NotBlank(message = "Informe o nome do grupo.")
        @Size(max = 80, message = "O nome pode ter até 80 caracteres.")
        String name,

        @NotNull(message = "Informe o mínimo de escolhas.")
        @Min(value = 0, message = "O mínimo não pode ser negativo.")
        @Max(value = 50, message = "O mínimo pode ser no máximo 50.")
        Integer minChoices,

        @NotNull(message = "Informe o máximo de escolhas.")
        @Min(value = 1, message = "O máximo deve ser pelo menos 1.")
        @Max(value = 50, message = "O máximo pode ser no máximo 50.")
        Integer maxChoices,

        @NotNull(message = "Informe como o grupo soma ao preço.")
        PricingRule pricingRule,

        @NotNull(message = "Informe se o grupo está ativo.")
        Boolean active,

        @NotEmpty(message = "Cadastre pelo menos uma opção.")
        @Size(max = 100, message = "O grupo pode ter até 100 opções.")
        List<@Valid @NotNull OptionItemRequest> options
) {
}
