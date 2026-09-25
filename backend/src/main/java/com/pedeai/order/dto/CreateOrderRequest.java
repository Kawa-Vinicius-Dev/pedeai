package com.pedeai.order.dto;

import com.pedeai.customer.dto.AddressRequest;
import com.pedeai.order.domain.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Pedido lançado pela equipe: balcão, telefone ou WhatsApp. O preço de cada item vem do cardápio. */
public record CreateOrderRequest(
        @NotNull(message = "Escolha retirada ou delivery.")
        OrderType type,

        @Valid
        OrderCustomerRequest customer,

        @Valid
        AddressRequest deliveryAddress,

        @NotEmpty(message = "Adicione pelo menos um item.")
        @Size(max = 100, message = "O pedido pode ter até 100 itens.")
        List<@Valid @NotNull OrderItemRequest> items,

        @Size(max = 500, message = "A observação pode ter até 500 caracteres.")
        String notes,

        @NotNull(message = "Informe o desconto (0 se não houver).")
        @Min(value = 0, message = "O desconto não pode ser negativo.")
        Long discountCents,

        @NotNull(message = "Informe a taxa de entrega (0 se não houver).")
        @Min(value = 0, message = "A taxa de entrega não pode ser negativa.")
        @Max(value = 100_000, message = "Taxa de entrega acima do permitido.")
        Long deliveryFeeCents,

        @NotNull(message = "Informe os pagamentos (pode ser uma lista vazia).")
        @Size(max = 5, message = "Use até 5 formas de pagamento.")
        List<@Valid @NotNull OrderPaymentRequest> payments
) {
}
