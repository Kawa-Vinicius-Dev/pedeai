package com.pedeai.customer.service;

import com.pedeai.customer.domain.DeliveryZone;
import com.pedeai.customer.dto.DeliveryZoneRequest;
import com.pedeai.customer.repository.DeliveryZoneRepository;
import com.pedeai.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

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
    void neighborhoodIsComparedWithoutAccentsOrCaseAndTheWarningNamesTheSavedOne() {
        when(repository.findByStoreIdAndNeighborhoodKey(STORE_ID, "sao jose"))
                .thenReturn(Optional.of(new DeliveryZone(STORE_ID, "São José", 700L, true, Instant.now(CLOCK))));

        assertThatThrownBy(() -> new DeliveryZoneService(repository, CLOCK)
                .create(STORE_ID, new DeliveryZoneRequest("  sao   JOSE ", 700L, true)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Já existe uma taxa para o bairro São José.");
    }

    @Test
    void theZoneItselfCanChangeTheSpelling() {
        DeliveryZone zone = new DeliveryZone(STORE_ID, "centro", 500L, true, Instant.now(CLOCK));
        when(repository.findByIdAndStoreId(zone.getId(), STORE_ID)).thenReturn(Optional.of(zone));
        when(repository.findByStoreIdAndNeighborhoodKey(STORE_ID, "centro")).thenReturn(Optional.of(zone));

        var updated = new DeliveryZoneService(repository, CLOCK)
                .update(STORE_ID, zone.getId(), new DeliveryZoneRequest("Centro", 600L, true));

        assertThat(updated.neighborhood()).isEqualTo("Centro");
        assertThat(updated.feeCents()).isEqualTo(600);
    }

    @Test
    void createsTheFee() {
        when(repository.findByStoreIdAndNeighborhoodKey(STORE_ID, "centro")).thenReturn(Optional.empty());
        when(repository.save(any(DeliveryZone.class))).then(returnsFirstArg());

        var zone = new DeliveryZoneService(repository, CLOCK).create(STORE_ID, new DeliveryZoneRequest("Centro", 500L,
                true));

        assertThat(zone.neighborhood()).isEqualTo("Centro");
        assertThat(zone.feeCents()).isEqualTo(500);
    }
}
