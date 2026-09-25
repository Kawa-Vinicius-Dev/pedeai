package com.pedeai.customer.controller;

import com.pedeai.customer.dto.AddressRequest;
import com.pedeai.customer.dto.CustomerAddressResponse;
import com.pedeai.customer.dto.CustomerRequest;
import com.pedeai.customer.dto.CustomerResponse;
import com.pedeai.customer.service.CustomerService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import com.pedeai.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@PreAuthorize(Permissions.TAKE_ORDERS)
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /** {@code phone} acha o cliente exato do telefone; {@code q} busca por parte do nome ou do telefone. */
    @GetMapping
    public PageResponse<CustomerResponse> search(CurrentUser user,
                                                 @RequestParam(required = false) String phone,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return customerService.search(user.storeId(), phone, q, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public CustomerResponse get(CurrentUser user, @PathVariable UUID id) {
        return customerService.get(user.storeId(), id);
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(CurrentUser user, @Valid @RequestBody CustomerRequest request) {
        CustomerResponse created = customerService.create(user.storeId(), request);
        return ResponseEntity.created(URI.create("/api/customers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(CurrentUser user, @PathVariable UUID id,
                                   @Valid @RequestBody CustomerRequest request) {
        return customerService.update(user.storeId(), id, request);
    }

    @PostMapping("/{id}/addresses")
    public ResponseEntity<CustomerAddressResponse> addAddress(CurrentUser user, @PathVariable UUID id,
                                                              @Valid @RequestBody AddressRequest request) {
        CustomerAddressResponse created = customerService.addAddress(user.storeId(), id, request);
        return ResponseEntity.created(URI.create("/api/customers/" + id + "/addresses/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}/addresses/{addressId}")
    public CustomerAddressResponse updateAddress(CurrentUser user, @PathVariable UUID id,
                                                 @PathVariable UUID addressId,
                                                 @Valid @RequestBody AddressRequest request) {
        return customerService.updateAddress(user.storeId(), id, addressId, request);
    }

    /** Tira o endereço da lista do cliente. Pedidos antigos guardam a cópia dele. */
    @DeleteMapping("/{id}/addresses/{addressId}")
    public ResponseEntity<Void> removeAddress(CurrentUser user, @PathVariable UUID id, @PathVariable UUID addressId) {
        customerService.removeAddress(user.storeId(), id, addressId);
        return ResponseEntity.noContent().build();
    }
}
