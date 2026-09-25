package com.pedeai.customer.dto;

import com.pedeai.customer.domain.AddressDraft;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Endereço de entrega. Usado no cadastro do cliente e direto no pedido. */
public record AddressRequest(
        @Size(max = 40, message = "O apelido pode ter até 40 caracteres.")
        String label,

        @NotBlank(message = "Informe a rua.")
        @Size(max = 120, message = "A rua pode ter até 120 caracteres.")
        String street,

        @NotBlank(message = "Informe o número (ou s/n).")
        @Size(max = 20, message = "O número pode ter até 20 caracteres.")
        String number,

        @Size(max = 80, message = "O complemento pode ter até 80 caracteres.")
        String complement,

        @NotBlank(message = "Informe o bairro.")
        @Size(max = 80, message = "O bairro pode ter até 80 caracteres.")
        String neighborhood,

        @Size(max = 80, message = "A cidade pode ter até 80 caracteres.")
        String city,

        @Size(max = 40, message = "O estado pode ter até 40 caracteres.")
        String state,

        @Size(max = 10, message = "O CEP pode ter até 10 caracteres.")
        String postalCode,

        @Size(max = 160, message = "A referência pode ter até 160 caracteres.")
        String reference
) {
    public AddressDraft toDraft() {
        return new AddressDraft(label, street, number, complement, neighborhood, city, state, postalCode, reference);
    }
}
