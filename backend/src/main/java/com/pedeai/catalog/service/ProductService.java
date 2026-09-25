package com.pedeai.catalog.service;

import com.pedeai.catalog.domain.Category;
import com.pedeai.catalog.domain.ItemPricing;
import com.pedeai.catalog.domain.ItemPricing.PricedItem;
import com.pedeai.catalog.domain.OptionGroup;
import com.pedeai.catalog.domain.Product;
import com.pedeai.catalog.domain.ProductDraft;
import com.pedeai.catalog.domain.Sector;
import com.pedeai.catalog.dto.PriceQuoteRequest;
import com.pedeai.catalog.dto.PriceQuoteResponse;
import com.pedeai.catalog.dto.PriceQuoteResponse.QuotedOptionResponse;
import com.pedeai.catalog.dto.ProductRequest;
import com.pedeai.catalog.dto.ProductResponse;
import com.pedeai.catalog.repository.CategoryRepository;
import com.pedeai.catalog.repository.OptionGroupRepository;
import com.pedeai.catalog.repository.ProductRepository;
import com.pedeai.catalog.repository.SectorRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.text.Texts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductService {
    static final String NOT_FOUND = "Produto não encontrado.";
    static final String INVALID_CATEGORY = "Categoria inválida.";
    static final String INVALID_SECTOR = "Setor inválido.";
    static final String INVALID_OPTION_GROUP = "Grupo de adicionais inválido.";
    static final String REPEATED_OPTION_GROUP = "O mesmo grupo de adicionais foi escolhido duas vezes.";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SectorRepository sectorRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final Clock clock;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          SectorRepository sectorRepository, OptionGroupRepository optionGroupRepository,
                          Clock clock) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.sectorRepository = sectorRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.clock = clock;
    }

    /**
     * O cardápio inteiro da loja (ou de uma categoria). Não é paginado: um cardápio tem algumas centenas de
     * itens no máximo, e o PDV precisa dele inteiro.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> list(UUID storeId, UUID categoryId) {
        List<Product> products = categoryId == null
                ? productRepository.findAllByStoreIdOrderBySortOrderAscNameAsc(storeId)
                : productRepository.findAllByStoreIdAndCategoryIdOrderBySortOrderAscNameAsc(storeId, categoryId);
        SectorResolver sectors = sectorResolver(storeId);
        return products.stream().map(product -> ProductResponse.from(product, sectors.resolve(product))).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID storeId, UUID id) {
        Product product = find(storeId, id);
        return ProductResponse.from(product, sectorResolver(storeId).resolve(product));
    }

    @Transactional
    public ProductResponse create(UUID storeId, ProductRequest request) {
        ProductDraft draft = validate(storeId, request, null);
        Product product = new Product(storeId, draft, productRepository.findMaxSortOrder(storeId) + 1,
                Instant.now(clock));
        productRepository.save(product);
        return ProductResponse.from(product, sectorResolver(storeId).resolve(product));
    }

    @Transactional
    public ProductResponse update(UUID storeId, UUID id, ProductRequest request) {
        Product product = find(storeId, id);
        product.update(validate(storeId, request, id), Instant.now(clock));
        return ProductResponse.from(product, sectorResolver(storeId).resolve(product));
    }

    /** Acabou durante o serviço: pausa o produto sem precisar editar o cadastro. */
    @Transactional
    public ProductResponse changeAvailability(UUID storeId, UUID id, boolean available) {
        Product product = find(storeId, id);
        product.changeAvailability(available, Instant.now(clock));
        return ProductResponse.from(product, sectorResolver(storeId).resolve(product));
    }

    /** Preço de um item montado, com as mesmas validações que o pedido vai aplicar. */
    @Transactional(readOnly = true)
    public PriceQuoteResponse quote(UUID storeId, UUID id, PriceQuoteRequest request) {
        Product product = find(storeId, id);
        List<ItemPricing.OptionChoice> choices = request.options().stream()
                .map(choice -> new ItemPricing.OptionChoice(choice.optionId(), choice.quantity()))
                .toList();
        PricedItem priced = ItemPricing.price(product, groupsInOrder(storeId, product.getOptionGroupIds()),
                request.quantity(), choices);
        List<QuotedOptionResponse> options = priced.options().stream()
                .map(option -> new QuotedOptionResponse(option.optionId(), option.groupId(), option.groupName(),
                        option.name(), option.code(), option.quantity(), option.unitPriceCents()))
                .toList();
        return new PriceQuoteResponse(product.getId(), product.getName(), product.getCode(),
                sectorResolver(storeId).resolve(product), priced.quantity(), priced.basePriceCents(),
                priced.optionsPriceCents(), priced.unitPriceCents(), priced.totalCents(), options);
    }

    private ProductDraft validate(UUID storeId, ProductRequest request, UUID productId) {
        categoryRepository.findByIdAndStoreId(request.categoryId(), storeId)
                .orElseThrow(() -> new BusinessRuleException(INVALID_CATEGORY));
        if (request.sectorId() != null && sectorRepository.findByIdAndStoreId(request.sectorId(), storeId).isEmpty()) {
            throw new BusinessRuleException(INVALID_SECTOR);
        }
        List<UUID> groupIds = request.optionGroupIds();
        if (new HashSet<>(groupIds).size() != groupIds.size()) {
            throw new BusinessRuleException(REPEATED_OPTION_GROUP);
        }
        if (!groupIds.isEmpty()
                && optionGroupRepository.findAllByStoreIdAndIdIn(storeId, groupIds).size() != groupIds.size()) {
            throw new BusinessRuleException(INVALID_OPTION_GROUP);
        }
        String code = Texts.trimToNull(request.code());
        if (code != null) {
            boolean taken = productId == null
                    ? productRepository.existsByStoreIdAndCode(storeId, code)
                    : productRepository.existsByStoreIdAndCodeAndIdNot(storeId, code, productId);
            if (taken) {
                throw new ConflictException("Já existe um produto com o código PDV " + code + ".");
            }
        }
        return new ProductDraft(request.categoryId(), code, request.name().trim(),
                Texts.trimToNull(request.description()), request.priceCents(), request.sectorId(), groupIds,
                request.available(), request.active());
    }

    private List<OptionGroup> groupsInOrder(UUID storeId, List<UUID> groupIds) {
        if (groupIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, OptionGroup> byId = optionGroupRepository.findAllByStoreIdAndIdIn(storeId, groupIds).stream()
                .collect(Collectors.toMap(OptionGroup::getId, Function.identity()));
        return groupIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private SectorResolver sectorResolver(UUID storeId) {
        Map<UUID, UUID> categorySectors = new HashMap<>();
        for (Category category : categoryRepository.findAllByStoreIdOrderBySortOrderAscNameAsc(storeId)) {
            categorySectors.put(category.getId(), category.getDefaultSectorId());
        }
        UUID storeDefault = sectorRepository.findByStoreIdAndDefaultSectorTrue(storeId).map(Sector::getId).orElse(null);
        return new SectorResolver(categorySectors, storeDefault);
    }

    private Product find(UUID storeId, UUID id) {
        return productRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /** Setor de produção do item: o do produto, senão o da categoria, senão o padrão da loja. */
    record SectorResolver(Map<UUID, UUID> categorySectors, UUID storeDefault) {
        UUID resolve(Product product) {
            if (product.getSectorId() != null) {
                return product.getSectorId();
            }
            UUID fromCategory = categorySectors.get(product.getCategoryId());
            return fromCategory != null ? fromCategory : storeDefault;
        }
    }
}
