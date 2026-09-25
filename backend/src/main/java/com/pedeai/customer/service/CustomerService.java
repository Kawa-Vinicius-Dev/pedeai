package com.pedeai.customer.service;

import com.pedeai.customer.domain.AddressDraft;
import com.pedeai.customer.domain.Customer;
import com.pedeai.customer.domain.CustomerAddress;
import com.pedeai.customer.domain.Phones;
import com.pedeai.customer.dto.AddressRequest;
import com.pedeai.customer.dto.CustomerAddressResponse;
import com.pedeai.customer.dto.CustomerLink;
import com.pedeai.customer.dto.CustomerRequest;
import com.pedeai.customer.dto.CustomerResponse;
import com.pedeai.customer.repository.CustomerRepository;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.text.Texts;
import com.pedeai.shared.web.PageResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Clientes dos canais próprios. O telefone identifica o cliente: digitar o telefone no pedido acha o cadastro. */
@Service
public class CustomerService {
    static final String NOT_FOUND = "Cliente não encontrado.";
    static final String DUPLICATE_PHONE = "Já existe um cliente com esse telefone.";

    private final CustomerRepository repository;
    private final Clock clock;

    public CustomerService(CustomerRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Com {@code phone}, procura o cliente exato daquele telefone. Com {@code q}, procura por parte do nome ou
     * do telefone. Sem nenhum dos dois, lista todos.
     */
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(UUID storeId, String phone, String q, Pageable pageable) {
        if (Texts.trimToNull(phone) != null) {
            String normalized = Phones.normalize(phone);
            List<CustomerResponse> found = repository.findByStoreIdAndPhone(storeId, normalized)
                    .map(customer -> List.of(CustomerResponse.from(customer)))
                    .orElse(List.of());
            return PageResponse.from(new PageImpl<>(found, pageable, found.size()));
        }
        String term = Texts.trimToNull(q);
        String namePattern = term == null ? null : "%" + term.toLowerCase(Locale.ROOT) + "%";
        String digits = term == null ? "" : term.replaceAll("\\D", "");
        // Sem dígitos na busca, o padrão do telefone não pode casar com nada ("%%" casaria com todos).
        String phonePattern = digits.isEmpty() ? "#" : "%" + digits + "%";
        return PageResponse.from(repository.search(storeId, namePattern, phonePattern, pageable)
                .map(CustomerResponse::from));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID storeId, UUID id) {
        return CustomerResponse.from(find(storeId, id));
    }

    @Transactional
    public CustomerResponse create(UUID storeId, CustomerRequest request) {
        String phone = Phones.normalize(request.phone());
        if (repository.findByStoreIdAndPhone(storeId, phone).isPresent()) {
            throw new ConflictException(DUPLICATE_PHONE);
        }
        Customer customer = new Customer(storeId, request.name().trim(), phone, Texts.trimToNull(request.email()),
                Texts.trimToNull(request.notes()), Instant.now(clock));
        return CustomerResponse.from(repository.save(customer));
    }

    @Transactional
    public CustomerResponse update(UUID storeId, UUID id, CustomerRequest request) {
        Customer customer = find(storeId, id);
        String phone = Phones.normalize(request.phone());
        if (repository.existsByStoreIdAndPhoneAndIdNot(storeId, phone, id)) {
            throw new ConflictException(DUPLICATE_PHONE);
        }
        customer.update(request.name().trim(), phone, Texts.trimToNull(request.email()),
                Texts.trimToNull(request.notes()), Instant.now(clock));
        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerAddressResponse addAddress(UUID storeId, UUID customerId, AddressRequest request) {
        Customer customer = find(storeId, customerId);
        return CustomerAddressResponse.from(customer.addAddress(request.toDraft(), Instant.now(clock)));
    }

    @Transactional
    public CustomerAddressResponse updateAddress(UUID storeId, UUID customerId, UUID addressId,
                                                 AddressRequest request) {
        Customer customer = find(storeId, customerId);
        return CustomerAddressResponse.from(customer.updateAddress(addressId, request.toDraft(), Instant.now(clock)));
    }

    @Transactional
    public void removeAddress(UUID storeId, UUID customerId, UUID addressId) {
        find(storeId, customerId).removeAddress(addressId, Instant.now(clock));
    }

    /**
     * Grava o cliente de um pedido feito por telefone ou no balcão: acha pelo telefone ou cadastra, atualiza o
     * nome se a pessoa corrigiu e guarda o endereço de entrega se ele for novo.
     *
     * @param address endereço de entrega, ou nulo na retirada
     */
    @Transactional
    public CustomerLink recordFromOrder(UUID storeId, String name, String phone, AddressRequest address) {
        String normalizedPhone = Phones.normalize(phone);
        String trimmedName = name.trim();
        Instant now = Instant.now(clock);
        Customer customer = repository.findByStoreIdAndPhone(storeId, normalizedPhone)
                .orElseGet(() -> repository.save(new Customer(storeId, trimmedName, normalizedPhone, null, null, now)));
        if (!customer.getName().equals(trimmedName)) {
            customer.rename(trimmedName, now);
        }
        if (address == null) {
            return new CustomerLink(customer.getId(), null, normalizedPhone);
        }
        AddressDraft draft = address.toDraft();
        CustomerAddress saved = customer.findSamePlace(draft).orElseGet(() -> customer.addAddress(draft, now));
        return new CustomerLink(customer.getId(), saved.getId(), normalizedPhone);
    }

    private Customer find(UUID storeId, UUID id) {
        return repository.findByIdAndStoreId(id, storeId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }
}
