package com.pedeai.storefront.dto;

import com.pedeai.customer.dto.AddressRequest;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.OrderItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Pedido feito pelo cliente no cardápio digital. Preço e taxa de entrega não vêm daqui: o servidor calcula pelo
 * cardápio e pela área de entrega. Paga na entrega ou na retirada.
 */
public record MenuOrderRequest(
        @NotNull(message = "Escolha entrega ou retirada.")
        OrderType type,

        @NotBlank(message = "Informe seu nome.")
        @Size(max = 120, message = "O nome pode ter até 120 caracteres.")
        String customerName,

        @NotBlank(message = "Informe seu telefone com DDD.")
        @Size(max = 30, message = "Telefone inválido.")
        String customerPhone,

        @Valid
        AddressRequest deliveryAddress,

        @NotEmpty(message = "Adicione pelo menos um item.")
        @Size(max = 50, message = "O pedido pode ter até 50 itens.")
        List<@Valid @NotNull OrderItemRequest> items,

        @Size(max = 500, message = "A observação pode ter até 500 caracteres.")
        String notes,

        @NotNull(message = "Escolha como vai pagar.")
        UUID paymentMethodId,

        @Min(value = 1, message = "O troco deve ser para um valor maior que zero.")
        @Max(value = 100_000_000, message = "Troco alto demais.")
        Long changeForCents
) {
}
