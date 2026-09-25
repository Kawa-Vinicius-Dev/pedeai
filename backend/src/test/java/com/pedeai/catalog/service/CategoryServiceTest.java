package com.pedeai.catalog.service;

import com.pedeai.catalog.domain.Category;
import com.pedeai.catalog.domain.Sector;
import com.pedeai.catalog.dto.CategoryRequest;
import com.pedeai.catalog.dto.CategoryResponse;
import com.pedeai.catalog.repository.CategoryRepository;
import com.pedeai.catalog.repository.SectorRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {
    @Mock
    private CategoryRepository repository;
    @Mock
    private SectorRepository sectorRepository;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(repository, sectorRepository, CLOCK);
    }

    @Test
    void createPutsTheCategoryAtTheEnd() {
        Sector bar = new Sector(STORE_ID, "Bar", false, 0, NOW);
        when(sectorRepository.findByIdAndStoreId(bar.getId(), STORE_ID)).thenReturn(Optional.of(bar));
        when(repository.findMaxSortOrder(STORE_ID)).thenReturn(4);
        when(repository.save(any(Category.class))).then(returnsFirstArg());

        CategoryResponse created = service.create(STORE_ID, new CategoryRequest(" Bebidas ", bar.getId(), null, true));

        assertThat(created.name()).isEqualTo("Bebidas");
        assertThat(created.sortOrder()).isEqualTo(5);
        assertThat(created.defaultSectorId()).isEqualTo(bar.getId());
    }

    @Test
    void rejectsSectorOfAnotherStore() {
        UUID foreignSector = UUID.randomUUID();
        when(sectorRepository.findByIdAndStoreId(foreignSector, STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(STORE_ID, new CategoryRequest("Bebidas", foreignSector, null, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(CategoryService.INVALID_SECTOR);
    }

    @Test
    void rejectsDuplicateName() {
        when(repository.existsByStoreIdAndNameIgnoreCase(STORE_ID, "Pizzas")).thenReturn(true);

        assertThatThrownBy(() -> service.create(STORE_ID, new CategoryRequest("Pizzas", null, null, true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateKeepsThePositionWhenNoneIsSent() {
        Category pizzas = new Category(STORE_ID, "Pizzas", null, 3, NOW);
        when(repository.findByIdAndStoreId(pizzas.getId(), STORE_ID)).thenReturn(Optional.of(pizzas));

        CategoryResponse updated = service.update(STORE_ID, pizzas.getId(),
                new CategoryRequest("Pizzas salgadas", null, null, false));

        assertThat(updated.sortOrder()).isEqualTo(3);
        assertThat(updated.name()).isEqualTo("Pizzas salgadas");
        assertThat(updated.active()).isFalse();
    }
}
