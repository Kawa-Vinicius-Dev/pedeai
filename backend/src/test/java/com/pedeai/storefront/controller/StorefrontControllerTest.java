package com.pedeai.storefront.controller;

import com.pedeai.shared.config.TimeConfig;
import com.pedeai.shared.security.SecurityConfig;
import com.pedeai.storefront.dto.MenuOrderResponse;
import com.pedeai.storefront.dto.StorefrontResponse;
import com.pedeai.storefront.service.StorefrontService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StorefrontController.class)
@Import({SecurityConfig.class, TimeConfig.class})
class StorefrontControllerTest {
    private static final String ORDER = """
            {"type":"TAKEOUT","customerName":"Maria","customerPhone":"11988880000",
             "items":[{"productId":"%s","quantity":1,"options":[]}],"paymentMethodId":"%s"}"""
            .formatted(UUID.randomUUID(), UUID.randomUUID());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StorefrontService storefrontService;

    @Test
    void menuIsPublicEvenWithAStaleToken() throws Exception {
        when(storefrontService.menu("pizzaria-bella")).thenReturn(new StorefrontResponse("Pizzaria Bella",
                "pizzaria-bella", null, true, List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/public/stores/pizzaria-bella"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pizzaria Bella"));
        mockMvc.perform(get("/api/public/stores/pizzaria-bella").header(HttpHeaders.AUTHORIZATION, "Bearer vencido"))
                .andExpect(status().isOk());
    }

    @Test
    void orderGoesThroughWithTheClientAddressAndPointsToTracking() throws Exception {
        when(storefrontService.placeOrder(eq("pizzaria-bella"), any(), anyString()))
                .thenReturn(new MenuOrderResponse(7, "codigo-aleatorio-1", 3000));

        mockMvc.perform(post("/api/public/stores/pizzaria-bella/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER).header("X-Forwarded-For", "203.0.113.9"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/public/orders/codigo-aleatorio-1"));
        verify(storefrontService).placeOrder(eq("pizzaria-bella"), any(), eq("203.0.113.9"));
    }

    @Test
    void invalidOrderAndOddTrackingCodeAreRefused() throws Exception {
        mockMvc.perform(post("/api/public/stores/pizzaria-bella/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TAKEOUT\",\"customerName\":\" \",\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/public/orders/curto"))
                .andExpect(status().isBadRequest());
        verify(storefrontService, never()).placeOrder(any(), any(), any());
        verify(storefrontService, never()).track(any());
    }
}
