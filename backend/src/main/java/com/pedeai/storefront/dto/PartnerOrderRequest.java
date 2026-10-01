package com.pedeai.storefront.dto;

import com.pedeai.customer.dto.AddressRequest;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.OrderItemRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Pedido criado por um sistema de terceiros pela API. Mesmas regras do cardápio digital: preço pelo cardápio, taxa pela
 * área de entrega e pagamento na entrega ou na retirada. {@code externalId}: o id do pedido no sistema de origem;
 * reenviar o mesmo devolve o pedido já criado, sem duplicar.
 */
public record PartnerOrderRequest(
        @Schema(types = {"string", "null"})
        @Size(max = 80, message = "O externalId pode ter até 80 caracteres.")
        @Pattern(regexp = "[A-Za-z0-9._:-]*", message = "Use só letras, números, ponto, hífen, dois-pontos e sublinhado.")
        String externalId,

        @NotNull(message = "Escolha entrega ou retirada.")
        OrderType type,

        @NotBlank(message = "Informe o nome do cliente.")
        @Size(max = 120, message = "O nome pode ter até 120 caracteres.")
        String customerName,

        @NotBlank(message = "Informe o telefone do cliente com DDD.")
        @Size(max = 30, message = "Telefone inválido.")
        String customerPhone,

        @Valid
        AddressRequest deliveryAddress,

        @NotEmpty(message = "Adicione pelo menos um item.")
        @Size(max = 50, message = "O pedido pode ter até 50 itens.")
        List<@Valid @NotNull OrderItemRequest> items,

        @Size(max = 500, message = "A observação pode ter até 500 caracteres.")
        String notes,

        @NotNull(message = "Informe a forma de pagamento.")
        UUID paymentMethodId,

        @Min(value = 1, message = "O troco deve ser para um valor maior que zero.")
        @Max(value = 100_000_000, message = "Troco alto demais.")
        Long changeForCents
) {
    public MenuOrderRequest toMenuOrder() {
        return new MenuOrderRequest(type, customerName, customerPhone, deliveryAddress, items, notes, paymentMethodId,
                changeForCents);
    }
}
