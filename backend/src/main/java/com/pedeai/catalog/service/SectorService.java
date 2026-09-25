package com.pedeai.catalog.service;

import com.pedeai.catalog.domain.Sector;
import com.pedeai.catalog.dto.SectorRequest;
import com.pedeai.catalog.dto.SectorResponse;
import com.pedeai.catalog.repository.SectorRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Setores de produção. A loja sempre tem exatamente um setor padrão (o primeiro criado vira padrão): é para
 * lá que vai o item sem setor definido no produto nem na categoria.
 */
@Service
public class SectorService {
    static final String NOT_FOUND = "Setor não encontrado.";
    static final String DUPLICATE_NAME = "Já existe um setor com esse nome.";
    static final String DEFAULT_REQUIRED =
            "A loja precisa de um setor padrão. Para trocar, marque outro setor como padrão.";
    static final String DEFAULT_MUST_BE_ACTIVE =
            "O setor padrão não pode ficar inativo. Marque outro setor como padrão antes.";

    private final SectorRepository repository;
    private final Clock clock;

    public SectorService(SectorRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SectorResponse> list(UUID storeId) {
        return repository.findAllByStoreIdOrderBySortOrderAscNameAsc(storeId).stream()
                .map(SectorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SectorResponse get(UUID storeId, UUID id) {
        return SectorResponse.from(find(storeId, id));
    }

    @Transactional
    public SectorResponse create(UUID storeId, SectorRequest request) {
        String name = request.name().trim();
        if (repository.existsByStoreIdAndNameIgnoreCase(storeId, name)) {
            throw new ConflictException(DUPLICATE_NAME);
        }
        Optional<Sector> currentDefault = repository.findByStoreIdAndDefaultSectorTrue(storeId);
        boolean makeDefault = request.defaultSector() || currentDefault.isEmpty();
        if (makeDefault && !request.active()) {
            throw new BusinessRuleException(DEFAULT_MUST_BE_ACTIVE);
        }
        Instant now = Instant.now(clock);
        if (makeDefault) {
            currentDefault.ifPresent(sector -> sector.changeDefault(false, now));
        }
        Sector sector = new Sector(storeId, name, makeDefault, repository.findMaxSortOrder(storeId) + 1, now);
        if (!request.active()) {
            sector.update(name, false, now);
        }
        return SectorResponse.from(repository.save(sector));
    }

    @Transactional
    public SectorResponse update(UUID storeId, UUID id, SectorRequest request) {
        Sector sector = find(storeId, id);
        String name = request.name().trim();
        if (repository.existsByStoreIdAndNameIgnoreCaseAndIdNot(storeId, name, id)) {
            throw new ConflictException(DUPLICATE_NAME);
        }
        if (sector.isDefaultSector() && !request.defaultSector()) {
            throw new BusinessRuleException(DEFAULT_REQUIRED);
        }
        if (request.defaultSector() && !request.active()) {
            throw new BusinessRuleException(DEFAULT_MUST_BE_ACTIVE);
        }
        Instant now = Instant.now(clock);
        if (request.defaultSector() && !sector.isDefaultSector()) {
            repository.findByStoreIdAndDefaultSectorTrue(storeId).ifPresent(current -> current.changeDefault(false, now));
            sector.changeDefault(true, now);
        }
        sector.update(name, request.active(), now);
        return SectorResponse.from(sector);
    }

    private Sector find(UUID storeId, UUID id) {
        return repository.findByIdAndStoreId(id, storeId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }
}
