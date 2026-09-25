package com.pedeai.catalog.controller;

import com.pedeai.catalog.domain.PricingRule;
import com.pedeai.catalog.dto.OptionGroupResponse;
import com.pedeai.catalog.dto.OptionItemResponse;
import com.pedeai.catalog.dto.PriceQuoteRequest;
import com.pedeai.catalog.dto.PriceQuoteResponse;
import com.pedeai.catalog.dto.ProductRequest;
import com.pedeai.catalog.dto.ProductResponse;
import com.pedeai.catalog.dto.SectorResponse;
import com.pedeai.catalog.service.CategoryService;
import com.pedeai.catalog.service.OptionGroupService;
import com.pedeai.catalog.service.ProductService;
import com.pedeai.catalog.service.SectorService;
import com.pedeai.shared.config.TimeConfig;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.security.Role;
import com.pedeai.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.pedeai.support.TestSecurity.STORE_ID;
import static com.pedeai.support.TestSecurity.as;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({SectorController.class, CategoryController.class, OptionGroupController.class, ProductController.class})
@Import({SecurityConfig.class, TimeConfig.class})
class CatalogControllersTest {
    private static final UUID PRODUCT_ID = UUID.fromString("01a0d567-0000-7000-8000-000000000010");
    private static final UUID CATEGORY_ID = UUID.fromString("01a0d567-0000-7000-8000-000000000011");
    private static final String PRODUCT_BODY = """
            {"categoryId":"%s","code":"500","name":"Pizza Grande","description":null,"priceCents":0,
             "sectorId":null,"optionGroupIds":[],"available":true,"active":true}
            """.formatted(CATEGORY_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SectorService sectorService;
    @MockitoBean
    private CategoryService categoryService;
    @MockitoBean
    private OptionGroupService optionGroupService;
    @MockitoBean
    private ProductService productService;

    @Test
    void anyoneFromTheStoreCanReadTheMenu() throws Exception {
        when(productService.list(STORE_ID, null)).thenReturn(List.of(product()));

        mockMvc.perform(get("/api/products").with(as(Role.WAITER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Pizza Grande"))
                .andExpect(jsonPath("$[0].priceCents").value(0))
                .andExpect(jsonPath("$[0].code").value("500"));
    }

    @Test
    void readingTheMenuRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void cashierCannotEditTheMenu() throws Exception {
        mockMvc.perform(post("/api/products").with(as(Role.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_BODY))
                .andExpect(status().isForbidden());
        verify(productService, never()).create(any(), any());
    }

    @Test
    void managerCreatesProductAndGetsItsLocation() throws Exception {
        when(productService.create(eq(STORE_ID), any(ProductRequest.class))).thenReturn(product());

        mockMvc.perform(post("/api/products").with(as(Role.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/products/" + PRODUCT_ID))
                .andExpect(jsonPath("$.effectiveSectorId").isEmpty());
    }

    @Test
    void productRequiresCategoryAndPrice() throws Exception {
        mockMvc.perform(post("/api/products").with(as(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pizza","optionGroupIds":[],"available":true,"active":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.categoryId").value("Escolha a categoria."))
                .andExpect(jsonPath("$.fields.priceCents").value("Informe o preço."));
    }

    @Test
    void cashierAndKitchenCanPauseAProductButWaiterCannot() throws Exception {
        when(productService.changeAvailability(STORE_ID, PRODUCT_ID, false)).thenReturn(product());
        String body = """
                {"available":false}
                """;

        mockMvc.perform(put("/api/products/{id}/availability", PRODUCT_ID).with(as(Role.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/products/{id}/availability", PRODUCT_ID).with(as(Role.KITCHEN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/products/{id}/availability", PRODUCT_ID).with(as(Role.WAITER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void quoteIsAvailableToEveryRoleAndReturns200() throws Exception {
        when(productService.quote(eq(STORE_ID), eq(PRODUCT_ID), any(PriceQuoteRequest.class))).thenReturn(
                new PriceQuoteResponse(PRODUCT_ID, "Pizza Grande", "500", null, 1, 0, 5290, 5290, 5290, List.of()));

        mockMvc.perform(post("/api/products/{id}/price-quotes", PRODUCT_ID).with(as(Role.WAITER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":1,"options":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPriceCents").value(5290));
    }

    @Test
    void quoteRuleViolationIs422() throws Exception {
        when(productService.quote(eq(STORE_ID), eq(PRODUCT_ID), any(PriceQuoteRequest.class)))
                .thenThrow(new BusinessRuleException("Escolha pelo menos 1 opção em Sabores."));

        mockMvc.perform(post("/api/products/{id}/price-quotes", PRODUCT_ID).with(as(Role.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":1,"options":[]}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Escolha pelo menos 1 opção em Sabores."));
    }

    @Test
    void unknownPricingRuleIsRejected() throws Exception {
        mockMvc.perform(post("/api/option-groups").with(as(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sabores","minChoices":1,"maxChoices":2,"pricingRule":"MEDIANA","active":true,
                                 "options":[{"name":"Calabresa","priceCents":4590,"available":true,"active":true}]}
                                """))
                .andExpect(status().isBadRequest());
        verify(optionGroupService, never()).create(any(), any());
    }

    @Test
    void optionGroupCreationReturnsTheOptions() throws Exception {
        UUID groupId = UUID.randomUUID();
        when(optionGroupService.create(eq(STORE_ID), any())).thenReturn(new OptionGroupResponse(groupId, "Sabores", 1, 2,
                PricingRule.MAX, true, List.of(new OptionItemResponse(UUID.randomUUID(), "101", "Calabresa", 4590,
                true, true))));

        mockMvc.perform(post("/api/option-groups").with(as(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sabores","minChoices":1,"maxChoices":2,"pricingRule":"MAX","active":true,
                                 "options":[{"code":"101","name":"Calabresa","priceCents":4590,"available":true,"active":true}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/option-groups/" + groupId))
                .andExpect(jsonPath("$.options[0].priceCents").value(4590));
    }

    @Test
    void sectorCreationValidatesTheName() throws Exception {
        when(sectorService.create(eq(STORE_ID), any())).thenReturn(new SectorResponse(UUID.randomUUID(), "Bar", false, true));

        mockMvc.perform(post("/api/sectors").with(as(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","defaultSector":false,"active":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").value("Informe o nome do setor."));
    }

    private static ProductResponse product() {
        return new ProductResponse(PRODUCT_ID, CATEGORY_ID, "500", "Pizza Grande", null, 0, null, null, List.of(),
                true, true);
    }
}
