package com.pedeai.catalog.service;

import com.pedeai.catalog.domain.Sector;
import com.pedeai.catalog.dto.SectorRequest;
import com.pedeai.catalog.dto.SectorResponse;
import com.pedeai.catalog.repository.SectorRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

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
class SectorServiceTest {
    @Mock
    private SectorRepository repository;

    private SectorService service;

    @BeforeEach
    void setUp() {
        service = new SectorService(repository, CLOCK);
    }

    @Test
    void theFirstSectorBecomesTheDefault() {
        when(repository.findByStoreIdAndDefaultSectorTrue(STORE_ID)).thenReturn(Optional.empty());
        when(repository.save(any(Sector.class))).then(returnsFirstArg());

        SectorResponse created = service.create(STORE_ID, new SectorRequest(" Cozinha ", false, true));

        assertThat(created.name()).isEqualTo("Cozinha");
        assertThat(created.defaultSector()).isTrue();
    }

    @Test
    void markingANewDefaultRemovesTheFlagFromTheOldOne() {
        Sector kitchen = new Sector(STORE_ID, "Cozinha", true, 0, NOW);
        when(repository.findByStoreIdAndDefaultSectorTrue(STORE_ID)).thenReturn(Optional.of(kitchen));
        when(repository.save(any(Sector.class))).then(returnsFirstArg());

        SectorResponse bar = service.create(STORE_ID, new SectorRequest("Bar", true, true));

        assertThat(bar.defaultSector()).isTrue();
        assertThat(kitchen.isDefaultSector()).isFalse();
    }

    @Test
    void rejectsDuplicateName() {
        when(repository.existsByStoreIdAndNameIgnoreCase(STORE_ID, "Cozinha")).thenReturn(true);

        assertThatThrownBy(() -> service.create(STORE_ID, new SectorRequest("Cozinha", false, true)))
                .isInstanceOf(ConflictException.class)
                .hasMessage(SectorService.DUPLICATE_NAME);
        verify(repository, never()).save(any());
    }

    @Test
    void theDefaultSectorCannotBeUnmarkedDirectly() {
        Sector kitchen = new Sector(STORE_ID, "Cozinha", true, 0, NOW);
        when(repository.findByIdAndStoreId(kitchen.getId(), STORE_ID)).thenReturn(Optional.of(kitchen));

        assertThatThrownBy(() -> service.update(STORE_ID, kitchen.getId(), new SectorRequest("Cozinha", false, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(SectorService.DEFAULT_REQUIRED);
    }

    @Test
    void theDefaultSectorCannotBeDeactivated() {
        Sector kitchen = new Sector(STORE_ID, "Cozinha", true, 0, NOW);
        when(repository.findByIdAndStoreId(kitchen.getId(), STORE_ID)).thenReturn(Optional.of(kitchen));

        assertThatThrownBy(() -> service.update(STORE_ID, kitchen.getId(), new SectorRequest("Cozinha", true, false)))
                .hasMessage(SectorService.DEFAULT_MUST_BE_ACTIVE);
    }

    @Test
    void updateMovesTheDefaultToThisSector() {
        Sector kitchen = new Sector(STORE_ID, "Cozinha", true, 0, NOW);
        Sector bar = new Sector(STORE_ID, "Bar", false, 1, NOW);
        when(repository.findByIdAndStoreId(bar.getId(), STORE_ID)).thenReturn(Optional.of(bar));
        when(repository.findByStoreIdAndDefaultSectorTrue(STORE_ID)).thenReturn(Optional.of(kitchen));

        SectorResponse updated = service.update(STORE_ID, bar.getId(), new SectorRequest("Bar", true, true));

        assertThat(updated.defaultSector()).isTrue();
        assertThat(kitchen.isDefaultSector()).isFalse();
    }

    @Test
    void updateDoesNotFindSectorsOfAnotherStore() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndStoreId(id, STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(STORE_ID, id, new SectorRequest("Bar", false, true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
