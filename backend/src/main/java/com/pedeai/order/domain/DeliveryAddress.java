package com.pedeai.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Cópia do endereço de entrega no pedido. Mudar o cadastro do cliente não altera pedido antigo. */
@Embeddable
public class DeliveryAddress {
    @Column(name = "delivery_street")
    private String street;
    @Column(name = "delivery_number")
    private String number;
    @Column(name = "delivery_complement")
    private String complement;
    @Column(name = "delivery_neighborhood")
    private String neighborhood;
    @Column(name = "delivery_city")
    private String city;
    @Column(name = "delivery_state")
    private String state;
    @Column(name = "delivery_postal_code")
    private String postalCode;
    @Column(name = "delivery_reference")
    private String reference;

    protected DeliveryAddress() {
    }

    public DeliveryAddress(String street, String number, String complement, String neighborhood, String city,
                           String state, String postalCode, String reference) {
        this.street = street;
        this.number = number;
        this.complement = complement;
        this.neighborhood = neighborhood;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.reference = reference;
    }

    public String getStreet() {
        return street;
    }

    public String getNumber() {
        return number;
    }

    public String getComplement() {
        return complement;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getReference() {
        return reference;
    }
}
