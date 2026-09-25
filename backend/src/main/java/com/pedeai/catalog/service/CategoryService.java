package com.pedeai.catalog.service;

import com.pedeai.catalog.domain.Category;
import com.pedeai.catalog.dto.CategoryRequest;
import com.pedeai.catalog.dto.CategoryResponse;
import com.pedeai.catalog.repository.CategoryRepository;
import com.pedeai.catalog.repository.SectorRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {
    static final String NOT_FOUND = "Categoria não encontrada.";
    static final String DUPLICATE_NAME = "Já existe uma categoria com esse nome.";
    static final String INVALID_SECTOR = "Setor inválido.";

    private final CategoryRepository repository;
    private final SectorRepository sectorRepository;
    private final Clock clock;

    public CategoryService(CategoryRepository repository, SectorRepository sectorRepository, Clock clock) {
        this.repository = repository;
        this.sectorRepository = sectorRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID storeId) {
        return repository.findAllByStoreIdOrderBySortOrderAscNameAsc(storeId).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID storeId, UUID id) {
        return CategoryResponse.from(find(storeId, id));
    }

    @Transactional
    public CategoryResponse create(UUID storeId, CategoryRequest request) {
        String name = request.name().trim();
        if (repository.existsByStoreIdAndNameIgnoreCase(storeId, name)) {
            throw new ConflictException(DUPLICATE_NAME);
        }
        validateSector(storeId, request.defaultSectorId());
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : repository.findMaxSortOrder(storeId) + 1;
        Instant now = Instant.now(clock);
        Category category = new Category(storeId, name, request.defaultSectorId(), sortOrder, now);
        if (!request.active()) {
            category.update(name, request.defaultSectorId(), sortOrder, false, now);
        }
        return CategoryResponse.from(repository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID storeId, UUID id, CategoryRequest request) {
        Category category = find(storeId, id);
        String name = request.name().trim();
        if (repository.existsByStoreIdAndNameIgnoreCaseAndIdNot(storeId, name, id)) {
            throw new ConflictException(DUPLICATE_NAME);
        }
        validateSector(storeId, request.defaultSectorId());
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : category.getSortOrder();
        category.update(name, request.defaultSectorId(), sortOrder, request.active(), Instant.now(clock));
        return CategoryResponse.from(category);
    }

    private void validateSector(UUID storeId, UUID sectorId) {
        if (sectorId != null && sectorRepository.findByIdAndStoreId(sectorId, storeId).isEmpty()) {
            throw new BusinessRuleException(INVALID_SECTOR);
        }
    }

    private Category find(UUID storeId, UUID id) {
        return repository.findByIdAndStoreId(id, storeId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }
}
