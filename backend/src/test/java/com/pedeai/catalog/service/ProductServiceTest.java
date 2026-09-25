package com.pedeai.catalog.service;

import com.pedeai.catalog.domain.Category;
import com.pedeai.catalog.domain.OptionDraft;
import com.pedeai.catalog.domain.OptionGroup;
import com.pedeai.catalog.domain.PricingRule;
import com.pedeai.catalog.domain.Product;
import com.pedeai.catalog.domain.ProductDraft;
import com.pedeai.catalog.domain.Sector;
import com.pedeai.catalog.dto.PriceQuoteRequest;
import com.pedeai.catalog.dto.PriceQuoteResponse;
import com.pedeai.catalog.dto.ProductRequest;
import com.pedeai.catalog.dto.ProductResponse;
import com.pedeai.catalog.repository.CategoryRepository;
import com.pedeai.catalog.repository.OptionGroupRepository;
import com.pedeai.catalog.repository.ProductRepository;
import com.pedeai.catalog.repository.SectorRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceTest {
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private SectorRepository sectorRepository;
    @Mock
    private OptionGroupRepository optionGroupRepository;

    private ProductService service;
    private Sector kitchen;
    private Sector bar;
    private Category pizzas;
    private Category drinks;
    private OptionGroup flavors;

    @BeforeEach
    void setUp() {
        service = new ProductService(productRepository, categoryRepository, sectorRepository, optionGroupRepository,
                CLOCK);
        kitchen = new Sector(STORE_ID, "Cozinha", true, 0, NOW);
        bar = new Sector(STORE_ID, "Bar", false, 1, NOW);
        pizzas = new Category(STORE_ID, "Pizzas", null, 0, NOW);
        drinks = new Category(STORE_ID, "Bebidas", bar.getId(), 1, NOW);
        flavors = new OptionGroup(STORE_ID, "Sabores", 1, 2, PricingRule.MAX, true, List.of(
                new OptionDraft(null, "101", "Calabresa", 4590, true, true),
                new OptionDraft(null, "102", "Quatro queijos", 5290, true, true)), NOW);

        when(categoryRepository.findAllByStoreIdOrderBySortOrderAscNameAsc(STORE_ID)).thenReturn(List.of(pizzas, drinks));
        when(categoryRepository.findByIdAndStoreId(pizzas.getId(), STORE_ID)).thenReturn(Optional.of(pizzas));
        when(categoryRepository.findByIdAndStoreId(drinks.getId(), STORE_ID)).thenReturn(Optional.of(drinks));
        when(sectorRepository.findByStoreIdAndDefaultSectorTrue(STORE_ID)).thenReturn(Optional.of(kitchen));
        when(sectorRepository.findByIdAndStoreId(bar.getId(), STORE_ID)).thenReturn(Optional.of(bar));
        when(optionGroupRepository.findAllByStoreIdAndIdIn(STORE_ID, List.of(flavors.getId()))).thenReturn(List.of(flavors));
    }

    @Test
    void theSectorComesFromTheProductThenTheCategoryThenTheStoreDefault() {
        Product pizza = product(pizzas, null, List.of());
        Product soda = product(drinks, null, List.of());
        Product dessertAtTheBar = product(pizzas, bar.getId(), List.of());
        when(productRepository.findAllByStoreIdOrderBySortOrderAscNameAsc(STORE_ID))
                .thenReturn(List.of(pizza, soda, dessertAtTheBar));

        List<ProductResponse> products = service.list(STORE_ID, null);

        assertThat(products).extracting(ProductResponse::effectiveSectorId)
                .containsExactly(kitchen.getId(), bar.getId(), bar.getId());
    }

    @Test
    void createNormalizesTextAndLinksTheGroups() {
        ProductResponse created = service.create(STORE_ID, request(pizzas.getId(), " 500 ", null, List.of(flavors.getId())));

        assertThat(created.code()).isEqualTo("500");
        assertThat(created.name()).isEqualTo("Pizza Grande");
        assertThat(created.description()).isNull();
        assertThat(created.optionGroupIds()).containsExactly(flavors.getId());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void rejectsCategoryOfAnotherStore() {
        UUID foreign = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(STORE_ID, request(foreign, null, null, List.of())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(ProductService.INVALID_CATEGORY);
    }

    @Test
    void rejectsSectorOfAnotherStore() {
        assertThatThrownBy(() -> service.create(STORE_ID, request(pizzas.getId(), null, UUID.randomUUID(), List.of())))
                .hasMessage(ProductService.INVALID_SECTOR);
    }

    @Test
    void rejectsOptionGroupOfAnotherStore() {
        UUID foreign = UUID.randomUUID();
        when(optionGroupRepository.findAllByStoreIdAndIdIn(STORE_ID, List.of(foreign))).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(STORE_ID, request(pizzas.getId(), null, null, List.of(foreign))))
                .hasMessage(ProductService.INVALID_OPTION_GROUP);
    }

    @Test
    void rejectsTheSameGroupTwice() {
        assertThatThrownBy(() -> service.create(STORE_ID,
                request(pizzas.getId(), null, null, List.of(flavors.getId(), flavors.getId()))))
                .hasMessage(ProductService.REPEATED_OPTION_GROUP);
        verify(optionGroupRepository, never()).findAllByStoreIdAndIdIn(any(), anyCollection());
    }

    @Test
    void rejectsPdvCodeInUseByAnotherProduct() {
        when(productRepository.existsByStoreIdAndCode(STORE_ID, "500")).thenReturn(true);

        assertThatThrownBy(() -> service.create(STORE_ID, request(pizzas.getId(), "500", null, List.of())))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Já existe um produto com o código PDV 500.");
    }

    @Test
    void updateMayKeepItsOwnPdvCode() {
        Product pizza = product(pizzas, null, List.of());
        when(productRepository.findByIdAndStoreId(pizza.getId(), STORE_ID)).thenReturn(Optional.of(pizza));
        when(productRepository.existsByStoreIdAndCodeAndIdNot(STORE_ID, "500", pizza.getId())).thenReturn(false);

        ProductResponse updated = service.update(STORE_ID, pizza.getId(), request(pizzas.getId(), "500", null, List.of()));

        assertThat(updated.code()).isEqualTo("500");
    }

    @Test
    void pausesTheProduct() {
        Product pizza = product(pizzas, null, List.of());
        when(productRepository.findByIdAndStoreId(pizza.getId(), STORE_ID)).thenReturn(Optional.of(pizza));

        assertThat(service.changeAvailability(STORE_ID, pizza.getId(), false).available()).isFalse();
    }

    @Test
    void quotePricesTheItemWithTheProductGroups() {
        Product pizza = product(pizzas, null, List.of(flavors.getId()));
        when(productRepository.findByIdAndStoreId(pizza.getId(), STORE_ID)).thenReturn(Optional.of(pizza));
        List<PriceQuoteRequest.OptionChoice> halfAndHalf = flavors.getOptions().stream()
                .map(option -> new PriceQuoteRequest.OptionChoice(option.getId(), 1))
                .toList();

        PriceQuoteResponse quote = service.quote(STORE_ID, pizza.getId(), new PriceQuoteRequest(2, halfAndHalf));

        assertThat(quote.unitPriceCents()).isEqualTo(5290);
        assertThat(quote.totalCents()).isEqualTo(10580);
        assertThat(quote.sectorId()).isEqualTo(kitchen.getId());
        assertThat(quote.options()).extracting(PriceQuoteResponse.QuotedOptionResponse::name)
                .containsExactly("Calabresa", "Quatro queijos");
    }

    @Test
    void productsOfAnotherStoreAreNotFound() {
        UUID id = UUID.randomUUID();
        when(productRepository.findByIdAndStoreId(id, STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(STORE_ID, id)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static ProductRequest request(UUID categoryId, String code, UUID sectorId, List<UUID> groupIds) {
        return new ProductRequest(categoryId, code, " Pizza Grande ", "  ", 0L, sectorId, groupIds, true, true);
    }

    private static Product product(Category category, UUID sectorId, List<UUID> groupIds) {
        return new Product(STORE_ID, new ProductDraft(category.getId(), null, "Produto", null, 0, sectorId, groupIds,
                true, true), 0, NOW);
    }
}
