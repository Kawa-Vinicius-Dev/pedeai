package com.pedeai.catalog.service;

import com.pedeai.catalog.domain.OptionDraft;
import com.pedeai.catalog.domain.OptionGroup;
import com.pedeai.catalog.domain.OptionItem;
import com.pedeai.catalog.domain.PricingRule;
import com.pedeai.catalog.dto.OptionGroupRequest;
import com.pedeai.catalog.dto.OptionGroupResponse;
import com.pedeai.catalog.dto.OptionItemRequest;
import com.pedeai.catalog.repository.OptionGroupRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.text.Texts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OptionGroupService {
    static final String NOT_FOUND = "Grupo de adicionais não encontrado.";
    static final String OPTION_NOT_FOUND = "Opção não encontrada.";
    static final String MIN_GREATER_THAN_MAX = "O mínimo de escolhas não pode ser maior que o máximo.";
    static final String NO_ACTIVE_OPTION = "Cadastre pelo menos uma opção ativa.";
    static final String NOT_ENOUGH_OPTIONS = "Há menos opções ativas do que o mínimo de escolhas.";
    static final String REPEATED_OPTION = "A mesma opção aparece duas vezes no grupo.";

    private final OptionGroupRepository repository;
    private final Clock clock;

    public OptionGroupService(OptionGroupRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<OptionGroupResponse> list(UUID storeId) {
        return repository.findAllByStoreIdOrderByNameAsc(storeId).stream().map(OptionGroupResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OptionGroupResponse get(UUID storeId, UUID id) {
        return OptionGroupResponse.from(find(storeId, id));
    }

    @Transactional
    public OptionGroupResponse create(UUID storeId, OptionGroupRequest request) {
        List<OptionDraft> drafts = validate(request);
        OptionGroup group = new OptionGroup(storeId, request.name().trim(), request.minChoices(),
                request.maxChoices(), request.pricingRule(), request.active(), drafts, Instant.now(clock));
        return OptionGroupResponse.from(repository.save(group));
    }

    @Transactional
    public OptionGroupResponse update(UUID storeId, UUID id, OptionGroupRequest request) {
        OptionGroup group = find(storeId, id);
        List<OptionDraft> drafts = validate(request);
        group.update(request.name().trim(), request.minChoices(), request.maxChoices(), request.pricingRule(),
                request.active(), drafts, Instant.now(clock));
        return OptionGroupResponse.from(group);
    }

    /** Esgotou um sabor ou adicional: pausa só a opção, sem editar o grupo. */
    @Transactional
    public OptionGroupResponse changeOptionAvailability(UUID storeId, UUID groupId, UUID optionId, boolean available) {
        OptionGroup group = find(storeId, groupId);
        OptionItem option = group.findOption(optionId)
                .orElseThrow(() -> new ResourceNotFoundException(OPTION_NOT_FOUND));
        option.changeAvailability(available, Instant.now(clock));
        return OptionGroupResponse.from(group);
    }

    private List<OptionDraft> validate(OptionGroupRequest request) {
        if (request.minChoices() > request.maxChoices()) {
            throw new BusinessRuleException(MIN_GREATER_THAN_MAX);
        }
        List<OptionDraft> drafts = request.options().stream().map(OptionGroupService::toDraft).toList();
        long activeOptions = drafts.stream().filter(OptionDraft::active).count();
        if (activeOptions == 0) {
            throw new BusinessRuleException(NO_ACTIVE_OPTION);
        }
        // Sabores não se repetem, então o mínimo precisa caber nas opções ativas. Em grupos de soma dá para
        // repetir a mesma opção (2x bacon), e a regra não se aplica.
        if (request.pricingRule() != PricingRule.SUM && request.minChoices() > activeOptions) {
            throw new BusinessRuleException(NOT_ENOUGH_OPTIONS);
        }
        Set<UUID> ids = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (OptionDraft draft : drafts) {
            if (draft.id() != null && !ids.add(draft.id())) {
                throw new BusinessRuleException(REPEATED_OPTION);
            }
            if (draft.code() != null && !codes.add(draft.code())) {
                throw new BusinessRuleException("Código PDV repetido no grupo: " + draft.code() + ".");
            }
        }
        return drafts;
    }

    private static OptionDraft toDraft(OptionItemRequest option) {
        return new OptionDraft(option.id(), Texts.trimToNull(option.code()), option.name().trim(), option.priceCents(),
                option.available(), option.active());
    }

    private OptionGroup find(UUID storeId, UUID id) {
        return repository.findByIdAndStoreId(id, storeId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }
}
