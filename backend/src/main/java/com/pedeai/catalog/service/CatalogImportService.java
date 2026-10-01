package com.pedeai.catalog.service;

import com.pedeai.catalog.domain.Category;
import com.pedeai.catalog.domain.Product;
import com.pedeai.catalog.dto.CatalogImportResponse;
import com.pedeai.catalog.dto.CategoryRequest;
import com.pedeai.catalog.dto.ImportDraft;
import com.pedeai.catalog.dto.ImportIssueResponse;
import com.pedeai.catalog.dto.OptionGroupRequest;
import com.pedeai.catalog.dto.OptionItemRequest;
import com.pedeai.catalog.dto.ProductRequest;
import com.pedeai.catalog.repository.CategoryRepository;
import com.pedeai.catalog.repository.OptionGroupRepository;
import com.pedeai.catalog.repository.ProductRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Importa um cardápio de fora (planilha ou iFood) para dentro do PedeAí (docs/06-roadmap.md#etapa-9). Categoria e grupo
 * de adicionais são casados pelo nome; produto, pelo código PDV e, sem código, pelo nome dentro da categoria. Tudo ou
 * nada: com qualquer erro, ou na pré-visualização, a transação é desfeita e nada fica gravado.
 */
@Service
public class CatalogImportService {
    static final int MAX_PRODUCTS = 2000;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final OptionGroupService optionGroupService;
    private final Validator validator;

    public CatalogImportService(CategoryRepository categoryRepository, ProductRepository productRepository,
                                OptionGroupRepository optionGroupRepository, CategoryService categoryService,
                                ProductService productService, OptionGroupService optionGroupService,
                                Validator validator) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.categoryService = categoryService;
        this.productService = productService;
        this.optionGroupService = optionGroupService;
        this.validator = validator;
    }

    @Transactional
    public CatalogImportResponse importSpreadsheet(UUID storeId, String content, boolean dryRun) {
        SpreadsheetParser.Result parsed = SpreadsheetParser.parse(content);
        if (!parsed.errors().isEmpty()) {
            return new CatalogImportResponse(false, 0, 0, 0, 0, parsed.errors());
        }
        return apply(storeId, parsed.draft(), dryRun);
    }

    @Transactional
    public CatalogImportResponse apply(UUID storeId, ImportDraft draft, boolean dryRun) {
        List<ImportIssueResponse> errors = new ArrayList<>();
        int total = draft.categories().stream().mapToInt(category -> category.products().size()).sum();
        if (total == 0) {
            errors.add(new ImportIssueResponse("cardápio", "Nenhum produto para importar."));
            return new CatalogImportResponse(false, 0, 0, 0, 0, errors);
        }
        if (total > MAX_PRODUCTS) {
            errors.add(new ImportIssueResponse("cardápio", "Importe até " + MAX_PRODUCTS + " produtos de cada vez."));
            return new CatalogImportResponse(false, 0, 0, 0, 0, errors);
        }
        Counts counts = new Counts();
        Map<String, UUID> groups = new HashMap<>();
        optionGroupRepository.findAllByStoreIdOrderByNameAsc(storeId)
                .forEach(group -> groups.put(key(group.getName()), group.getId()));
        Set<String> codesInDraft = new HashSet<>();
        for (ImportDraft.Category draftCategory : draft.categories()) {
            UUID categoryId = category(storeId, draftCategory.name(), counts, errors);
            if (categoryId == null) {
                continue;
            }
            for (ImportDraft.Product product : draftCategory.products()) {
                if (product.code() != null && !codesInDraft.add(key(product.code()))) {
                    errors.add(new ImportIssueResponse(product.origin(), "O código " + product.code()
                            + " aparece em mais de um produto."));
                    continue;
                }
                List<UUID> groupIds = groupIds(storeId, product, groups, counts, errors);
                if (groupIds != null) {
                    product(storeId, categoryId, product, groupIds, counts, errors);
                }
            }
        }
        boolean applied = errors.isEmpty() && !dryRun;
        if (!applied) {
            // Pré-visualização, ou algum erro: desfaz tudo o que este pedido de importação fez.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return new CatalogImportResponse(applied, counts.categories, counts.created, counts.updated, counts.groups,
                errors);
    }

    private UUID category(UUID storeId, String name, Counts counts, List<ImportIssueResponse> errors) {
        Optional<Category> existing = categoryRepository.findAllByStoreIdOrderBySortOrderAscNameAsc(storeId).stream()
                .filter(category -> category.getName().equalsIgnoreCase(name.trim()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        CategoryRequest request = new CategoryRequest(name.trim(), null, null, true);
        if (!valid(request, "categoria " + name, errors)) {
            return null;
        }
        counts.categories++;
        return categoryService.create(storeId, request).id();
    }

    /** Grupos do produto: os da planilha precisam existir; os do iFood são criados se ainda não houver. */
    private List<UUID> groupIds(UUID storeId, ImportDraft.Product product, Map<String, UUID> groups, Counts counts,
                                List<ImportIssueResponse> errors) {
        List<UUID> ids = new ArrayList<>();
        for (String name : product.groupNames()) {
            UUID id = groups.get(key(name));
            if (id == null) {
                errors.add(new ImportIssueResponse(product.origin(), "Grupo de adicionais \"" + name
                        + "\" não existe. Cadastre o grupo antes ou corrija o nome."));
                return null;
            }
            ids.add(id);
        }
        for (ImportDraft.Group group : product.groups()) {
            UUID id = groups.get(key(group.name()));
            if (id == null) {
                OptionGroupRequest request = new OptionGroupRequest(group.name(), group.minChoices(),
                        Math.max(1, group.maxChoices()), group.pricingRule(), true, group.options().stream()
                        .map(option -> new OptionItemRequest(null, option.code(), option.name(), option.priceCents(),
                                true, true))
                        .toList());
                if (!valid(request, product.origin() + ", grupo " + group.name(), errors)) {
                    return null;
                }
                try {
                    id = optionGroupService.create(storeId, request).id();
                } catch (BusinessRuleException | ConflictException e) {
                    errors.add(new ImportIssueResponse(product.origin(), e.getMessage()));
                    return null;
                }
                groups.put(key(group.name()), id);
                counts.groups++;
            }
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private void product(UUID storeId, UUID categoryId, ImportDraft.Product draft, List<UUID> groupIds,
                         Counts counts, List<ImportIssueResponse> errors) {
        Optional<Product> existing = draft.code() == null ? Optional.empty()
                : productRepository.findByStoreIdAndCode(storeId, draft.code());
        if (existing.isEmpty()) {
            existing = productRepository.findAllByStoreIdAndCategoryIdOrderBySortOrderAscNameAsc(storeId, categoryId)
                    .stream().filter(product -> product.getName().equalsIgnoreCase(draft.name().trim())).findFirst();
        }
        try {
            if (existing.isPresent()) {
                Product current = existing.get();
                ProductRequest request = new ProductRequest(categoryId,
                        draft.code() == null ? current.getCode() : draft.code(), draft.name().trim(),
                        draft.description() == null ? current.getDescription() : draft.description(),
                        draft.priceCents(), current.getSectorId(),
                        groupIds.isEmpty() ? List.copyOf(current.getOptionGroupIds()) : groupIds,
                        draft.available(), true, current.isSellOnIfood(), current.getIfoodPriceCents());
                if (valid(request, draft.origin(), errors)) {
                    productService.update(storeId, current.getId(), request);
                    counts.updated++;
                }
            } else {
                ProductRequest request = new ProductRequest(categoryId, draft.code(), draft.name().trim(),
                        draft.description(), draft.priceCents(), null, groupIds, draft.available(), true, null, null);
                if (valid(request, draft.origin(), errors)) {
                    productService.create(storeId, request);
                    counts.created++;
                }
            }
        } catch (BusinessRuleException | ConflictException e) {
            errors.add(new ImportIssueResponse(draft.origin(), e.getMessage()));
        }
    }

    /** As mesmas regras (e mensagens) das telas de cadastro. */
    private boolean valid(Object request, String origin, List<ImportIssueResponse> errors) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        violations.forEach(violation -> errors.add(new ImportIssueResponse(origin, violation.getMessage())));
        return violations.isEmpty();
    }

    private static String key(String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Counts {
        int categories;
        int created;
        int updated;
        int groups;
    }
}
