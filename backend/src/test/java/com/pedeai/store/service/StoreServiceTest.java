package com.pedeai.store.service;

import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.store.domain.Store;
import com.pedeai.store.dto.StoreResponse;
import com.pedeai.store.dto.UpdateStoreRequest;
import com.pedeai.store.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {
    @Mock
    private StoreRepository storeRepository;

    private StoreService service;
    private Store store;

    @BeforeEach
    void setUp() {
        service = new StoreService(storeRepository, CLOCK);
        store = new Store("Pizzaria Bella", NOW.minusSeconds(3600));
    }

    @Test
    void getReturnsStoreWithDefaults() {
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

        StoreResponse response = service.get(STORE_ID);

        assertThat(response.name()).isEqualTo("Pizzaria Bella");
        assertThat(response.timezone()).isEqualTo("America/Sao_Paulo");
        assertThat(response.businessDayCutoff()).isEqualTo(LocalTime.of(5, 0));
        assertThat(response.serviceFeeBp()).isEqualTo(1000);
        assertThat(response.autoConfirmOwnOrders()).isTrue();
    }

    @Test
    void getThrowsNotFoundForUnknownStore() {
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(STORE_ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateChangesOnlyTheInformedFields() {
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

        StoreResponse response = service.update(STORE_ID, new UpdateStoreRequest(
                null, null, "(11) 99999-0000", null, LocalTime.of(4, 30), 1200, null, true));

        assertThat(response.name()).isEqualTo("Pizzaria Bella");
        assertThat(response.phone()).isEqualTo("(11) 99999-0000");
        assertThat(response.businessDayCutoff()).isEqualTo(LocalTime.of(4, 30));
        assertThat(response.serviceFeeBp()).isEqualTo(1200);
        assertThat(response.autoConfirmOwnOrders()).isTrue();
        assertThat(response.startPreparationOnConfirm()).isTrue();
        assertThat(store.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void blankTextClearsDocumentAndPhone() {
        store.update(store.getName(), "12.345.678/0001-90", "1199", store.getTimezone(),
                store.getBusinessDayCutoff(), 1000, true, false, NOW);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

        StoreResponse response = service.update(STORE_ID, new UpdateStoreRequest(
                null, "", " ", null, null, null, null, null));

        assertThat(response.document()).isNull();
        assertThat(response.phone()).isNull();
    }

    @Test
    void updateRejectsInvalidTimeZone() {
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

        assertThatThrownBy(() -> service.update(STORE_ID, new UpdateStoreRequest(
                null, null, null, "Marte/Olympus", null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }
}
