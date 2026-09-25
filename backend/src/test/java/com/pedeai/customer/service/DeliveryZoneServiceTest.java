package com.pedeai.customer.service;

import com.pedeai.customer.domain.DeliveryZone;
import com.pedeai.customer.dto.DeliveryZoneRequest;
import com.pedeai.customer.repository.DeliveryZoneRepository;
import com.pedeai.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryZoneServiceTest {
    @Mock
    private DeliveryZoneRepository repository;

    @Test
    void neighborhoodIsComparedWithoutAccentsOrCase() {
        when(repository.existsByStoreIdAndNeighborhoodKey(STORE_ID, "sao jose")).thenReturn(true);

        assertThatThrownBy(() -> new DeliveryZoneService(repository, CLOCK)
                .create(STORE_ID, new DeliveryZoneRequest("  São   José ", 700L, true)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Já existe uma taxa para o bairro São José.");
    }

    @Test
    void createsTheFee() {
        when(repository.existsByStoreIdAndNeighborhoodKey(STORE_ID, "centro")).thenReturn(false);
        when(repository.save(any(DeliveryZone.class))).then(returnsFirstArg());

        var zone = new DeliveryZoneService(repository, CLOCK).create(STORE_ID, new DeliveryZoneRequest("Centro", 500L,
                true));

        assertThat(zone.neighborhood()).isEqualTo("Centro");
        assertThat(zone.feeCents()).isEqualTo(500);
    }
}
