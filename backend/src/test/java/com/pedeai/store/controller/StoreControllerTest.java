package com.pedeai.store.controller;

import com.pedeai.shared.config.TimeConfig;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ForbiddenOperationException;
import com.pedeai.shared.security.Role;
import com.pedeai.shared.security.SecurityConfig;
import com.pedeai.store.dto.AuthResponse;
import com.pedeai.store.dto.StoreResponse;
import com.pedeai.store.dto.StoreSummaryResponse;
import com.pedeai.store.dto.UpdateStoreRequest;
import com.pedeai.store.dto.UserResponse;
import com.pedeai.store.service.AuthResult;
import com.pedeai.store.service.IssuedRefreshToken;
import com.pedeai.store.service.StoreRegistrationService;
import com.pedeai.store.service.StoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;

import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static com.pedeai.support.TestSecurity.USER_ID;
import static com.pedeai.support.TestSecurity.as;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StoreController.class)
@Import({SecurityConfig.class, TimeConfig.class, RefreshTokenCookies.class})
class StoreControllerTest {
    private static final String REGISTER_BODY = """
            {"storeName":"Pizzaria Bella","ownerName":"Ana","email":"ana@example.com","password":"senha-forte-1"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StoreRegistrationService registrationService;
    @MockitoBean
    private StoreService storeService;

    @Test
    void registerReturns201WithLocationAndSessionCookie() throws Exception {
        AuthResponse response = new AuthResponse("jwt", "Bearer", 900,
                new UserResponse(USER_ID, "Ana", "ana@example.com", Role.OWNER, true, NOW),
                new StoreSummaryResponse(STORE_ID, "Pizzaria Bella"));
        when(registrationService.register(any(), any()))
                .thenReturn(new AuthResult(response, new IssuedRefreshToken("refresh-value", NOW)));

        mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/store"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("pedeai_refresh=refresh-value")))
                .andExpect(jsonPath("$.accessToken").value("jwt"))
                .andExpect(jsonPath("$.user.role").value("OWNER"));
    }

    @Test
    void registerValidatesPasswordLength() throws Exception {
        mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON).content("""
                        {"storeName":"Pizzaria","ownerName":"Ana","email":"ana@example.com","password":"curta"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").value("A senha deve ter entre 8 e 72 caracteres."));
        verify(registrationService, never()).register(any(), any());
    }

    @Test
    void registerReturns409WhenEmailIsTaken() throws Exception {
        when(registrationService.register(any(), any())).thenThrow(new ConflictException("Este e-mail já está em uso."));

        mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Este e-mail já está em uso."));
    }

    @Test
    void registerReturns403WhenSignupIsDisabled() throws Exception {
        when(registrationService.register(any(), any()))
                .thenThrow(new ForbiddenOperationException("O cadastro de novas lojas está desativado."));

        mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/store"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Faça login para continuar."))
                .andExpect(jsonPath("$.path").value("/api/store"));
    }

    @Test
    void getReturnsTheStoreFromTheToken() throws Exception {
        when(storeService.get(STORE_ID)).thenReturn(storeResponse());

        mockMvc.perform(get("/api/store").with(as(Role.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pizzaria Bella"))
                .andExpect(jsonPath("$.serviceFeeBp").value(1000));
    }

    @Test
    void updateIsOnlyForOwners() throws Exception {
        mockMvc.perform(patch("/api/store").with(as(Role.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Outro nome"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Você não tem permissão para esta ação."));
        verify(storeService, never()).update(any(), any());
    }

    @Test
    void updateValidatesServiceFee() throws Exception {
        mockMvc.perform(patch("/api/store").with(as(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceFeeBp":5000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.serviceFeeBp").value("A taxa de serviço pode ser no máximo 30%."));
    }

    @Test
    void updatePassesTheStoreFromTheToken() throws Exception {
        when(storeService.update(eq(STORE_ID), any(UpdateStoreRequest.class))).thenReturn(storeResponse());

        mockMvc.perform(patch("/api/store").with(as(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessDayCutoff":"04:30","autoConfirmOwnOrders":false}
                                """))
                .andExpect(status().isOk());
        verify(storeService).update(STORE_ID,
                new UpdateStoreRequest(null, null, null, null, LocalTime.of(4, 30), null, false, null));
    }

    private static StoreResponse storeResponse() {
        return new StoreResponse(STORE_ID, "Pizzaria Bella", null, null, "America/Sao_Paulo",
                LocalTime.of(5, 0), 1000, true, false);
    }
}
