package com.pedeai.storefront.dto;

import com.pedeai.storefront.domain.CouponType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/** {@code amount}: pontos-base no cupom de porcentagem (1000 = 10%), centavos no de valor fixo. */
public record CouponRequest(
        @NotBlank(message = "Informe o código do cupom.")
        @Pattern(regexp = "[A-Za-z0-9 ]{3,30}", message = "Use de 3 a 30 letras e números, sem acento.")
        String code,

        @NotNull(message = "Escolha o tipo do desconto.")
        CouponType type,

        @NotNull(message = "Informe o valor do desconto.")
        @Min(value = 1, message = "O desconto deve ser maior que zero.")
        @Max(value = 10_000_000, message = "Desconto alto demais.")
        Long amount,

        @NotNull(message = "Informe o pedido mínimo (0 se não houver).")
        @Min(value = 0, message = "O pedido mínimo não pode ser negativo.")
        @Max(value = 10_000_000, message = "Pedido mínimo alto demais.")
        Long minOrderCents,

        @Schema(types = {"integer", "null"})
        @Min(value = 1, message = "O limite de usos deve ser pelo menos 1.")
        Integer maxUses,

        @Schema(types = {"string", "null"})
        LocalDate validUntil,

        @NotNull(message = "Informe se o cupom está ativo.")
        Boolean active
) {
}
