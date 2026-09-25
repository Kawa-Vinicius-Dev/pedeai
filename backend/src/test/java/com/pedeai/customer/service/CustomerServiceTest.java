package com.pedeai.customer.service;

import com.pedeai.customer.domain.Customer;
import com.pedeai.customer.dto.AddressRequest;
import com.pedeai.customer.dto.CustomerLink;
import com.pedeai.customer.dto.CustomerRequest;
import com.pedeai.customer.repository.CustomerRepository;
import com.pedeai.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {
    private static final String PHONE = "+5511999990000";
    private static final AddressRequest HOME =
            new AddressRequest("Casa", "Rua das Flores", "120", "Ap 32", "Centro", null, null, null, null);

    @Mock
    private CustomerRepository repository;

    private CustomerService service() {
        return new CustomerService(repository, CLOCK);
    }

    @Test
    void createNormalizesThePhoneAndRejectsDuplicates() {
        when(repository.findByStoreIdAndPhone(STORE_ID, PHONE)).thenReturn(Optional.empty());
        when(repository.save(any(Customer.class))).then(returnsFirstArg());

        assertThat(service().create(STORE_ID, new CustomerRequest(" Maria ", "(11) 99999-0000", null, null)).phone())
                .isEqualTo(PHONE);

        when(repository.findByStoreIdAndPhone(STORE_ID, PHONE))
                .thenReturn(Optional.of(new Customer(STORE_ID, "Maria", PHONE, null, null, NOW)));
        assertThatThrownBy(() -> service().create(STORE_ID, new CustomerRequest("Outra", "11 99999 0000", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage(CustomerService.DUPLICATE_PHONE);
    }

    @Test
    void firstOrderCreatesTheCustomerWithTheAddress() {
        when(repository.findByStoreIdAndPhone(STORE_ID, PHONE)).thenReturn(Optional.empty());
        when(repository.save(any(Customer.class))).then(returnsFirstArg());

        CustomerLink link = service().recordFromOrder(STORE_ID, "Maria", "(11) 99999-0000", HOME);

        assertThat(link.customerId()).isNotNull();
        assertThat(link.addressId()).isNotNull();
        assertThat(link.phone()).isEqualTo(PHONE);
    }

    @Test
    void nextOrderReusesTheSameAddressAndKeepsTheLatestName() {
        Customer maria = new Customer(STORE_ID, "Maria", PHONE, null, null, NOW);
        var saved = maria.addAddress(HOME.toDraft(), NOW);
        when(repository.findByStoreIdAndPhone(STORE_ID, PHONE)).thenReturn(Optional.of(maria));

        // Mesma rua e número, digitados de outro jeito.
        AddressRequest sameHome = new AddressRequest(null, "rua das flores", " 120 ", "ap 32", "Centro", null, null,
                null, null);
        CustomerLink link = service().recordFromOrder(STORE_ID, "Maria Souza", "11999990000", sameHome);

        assertThat(link.addressId()).isEqualTo(saved.getId());
        assertThat(maria.getActiveAddresses()).hasSize(1);
        assertThat(maria.getName()).isEqualTo("Maria Souza");
        verify(repository, never()).save(any());
    }

    @Test
    void newPlaceBecomesAnotherAddress() {
        Customer maria = new Customer(STORE_ID, "Maria", PHONE, null, null, NOW);
        maria.addAddress(HOME.toDraft(), NOW);
        when(repository.findByStoreIdAndPhone(STORE_ID, PHONE)).thenReturn(Optional.of(maria));

        service().recordFromOrder(STORE_ID, "Maria", PHONE,
                new AddressRequest("Trabalho", "Av. Paulista", "1000", null, "Bela Vista", null, null, null, null));

        assertThat(maria.getActiveAddresses()).extracting("street").containsExactly("Rua das Flores", "Av. Paulista");
    }
}
