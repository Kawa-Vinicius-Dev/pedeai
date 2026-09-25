package com.pedeai.payment.service;

import com.pedeai.payment.domain.PaymentMethod;
import com.pedeai.payment.domain.PaymentMethodType;
import com.pedeai.payment.dto.PaymentMethodRequest;
import com.pedeai.payment.repository.PaymentMethodRepository;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.store.event.StoreRegistered;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentMethodServiceTest {
    @Mock
    private PaymentMethodRepository repository;

    @Test
    void newStoreGetsTheDefaultMethods() {
        when(repository.existsByStoreId(STORE_ID)).thenReturn(false);

        new PaymentMethodService(repository, CLOCK).onStoreRegistered(new StoreRegistered(STORE_ID));

        ArgumentCaptor<PaymentMethod> saved = ArgumentCaptor.forClass(PaymentMethod.class);
        verify(repository, times(7)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(PaymentMethod::getName)
                .containsExactly("Dinheiro", "Pix", "Crédito", "Débito", "Vale-refeição", "Online iFood",
                        "Online 99Food");
        assertThat(saved.getAllValues().getFirst().getType()).isEqualTo(PaymentMethodType.CASH);
    }

    @Test
    void defaultsAreNotCreatedTwice() {
        when(repository.existsByStoreId(STORE_ID)).thenReturn(true);

        new PaymentMethodService(repository, CLOCK).onStoreRegistered(new StoreRegistered(STORE_ID));

        verify(repository, never()).save(any());
    }

    @Test
    void nameIsUniqueInTheStore() {
        when(repository.existsByStoreIdAndNameIgnoreCase(STORE_ID, "Pix")).thenReturn(true);

        assertThatThrownBy(() -> new PaymentMethodService(repository, CLOCK)
                .create(STORE_ID, new PaymentMethodRequest(" Pix ", PaymentMethodType.PIX, true)))
                .isInstanceOf(ConflictException.class)
                .hasMessage(PaymentMethodService.DUPLICATE_NAME);
    }
}
