package com.pedeai.store.controller;

import com.pedeai.shared.config.TimeConfig;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.security.Role;
import com.pedeai.shared.security.SecurityConfig;
import com.pedeai.store.dto.CreateUserRequest;
import com.pedeai.store.dto.MeResponse;
import com.pedeai.store.dto.StoreSummaryResponse;
import com.pedeai.store.dto.UserResponse;
import com.pedeai.store.service.UserService;
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

import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static com.pedeai.support.TestSecurity.USER_ID;
import static com.pedeai.support.TestSecurity.as;
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

@WebMvcTest({UserController.class, MeController.class})
@Import({SecurityConfig.class, TimeConfig.class})
class UserControllerTest {
    private static final UUID CASHIER_ID = UUID.fromString("01a0d567-0000-7000-8000-000000000003");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void listIsOnlyForOwners() throws Exception {
        mockMvc.perform(get("/api/users").with(as(Role.CASHIER)))
                .andExpect(status().isForbidden());
        verify(userService, never()).list(any());
    }

    @Test
    void listReturnsTheTeamOfTheOwnersStore() throws Exception {
        when(userService.list(STORE_ID)).thenReturn(List.of(cashier()));

        mockMvc.perform(get("/api/users").with(as(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Caio"))
                .andExpect(jsonPath("$[0].role").value("CASHIER"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        when(userService.create(eq(STORE_ID), any(CreateUserRequest.class))).thenReturn(cashier());

        mockMvc.perform(post("/api/users").with(as(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Caio","email":"caio@example.com","password":"senha-do-caio","role":"CASHIER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/users/" + CASHIER_ID))
                .andExpect(jsonPath("$.email").value("caio@example.com"));
    }

    @Test
    void createRequiresARole() throws Exception {
        mockMvc.perform(post("/api/users").with(as(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Caio","email":"caio@example.com","password":"senha-do-caio"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.role").value("Informe o papel."));
    }

    @Test
    void getReturns404ForUserOfAnotherStore() throws Exception {
        when(userService.get(STORE_ID, CASHIER_ID)).thenThrow(new ResourceNotFoundException("Usuário não encontrado."));

        mockMvc.perform(get("/api/users/{id}", CASHIER_ID).with(as(Role.OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuário não encontrado."));
    }

    @Test
    void getRejectsMalformedId() throws Exception {
        mockMvc.perform(get("/api/users/{id}", "nao-e-uuid").with(as(Role.OWNER)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReturns409WhenRemovingTheLastOwner() throws Exception {
        when(userService.update(eq(STORE_ID), eq(USER_ID), any()))
                .thenThrow(new ConflictException("A loja precisa de pelo menos um dono ativo."));

        mockMvc.perform(patch("/api/users/{id}", USER_ID).with(as(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"MANAGER"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A loja precisa de pelo menos um dono ativo."));
    }

    @Test
    void meWorksForAnyRole() throws Exception {
        when(userService.me(STORE_ID, USER_ID)).thenReturn(new MeResponse(
                new UserResponse(USER_ID, "Ana", "ana@example.com", Role.KITCHEN, true, NOW),
                new StoreSummaryResponse(STORE_ID, "Pizzaria Bella")));

        mockMvc.perform(get("/api/me").with(as(Role.KITCHEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("KITCHEN"))
                .andExpect(jsonPath("$.store.name").value("Pizzaria Bella"));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    private static UserResponse cashier() {
        return new UserResponse(CASHIER_ID, "Caio", "caio@example.com", Role.CASHIER, true, NOW);
    }
}
