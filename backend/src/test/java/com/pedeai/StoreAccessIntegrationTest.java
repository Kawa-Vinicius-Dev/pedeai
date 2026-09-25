package com.pedeai;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import jakarta.servlet.http.Cookie;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo com banco (H2 no modo PostgreSQL, ou PostgreSQL real no CI) e segurança de verdade.
 * A janela de tolerância de renovação fica zerada para o reuso de token ser detectado na hora.
 */
@SpringBootTest(properties = "app.auth.refresh-reuse-grace=PT0S")
@AutoConfigureMockMvc
class StoreAccessIntegrationTest {
    private static final String REFRESH_COOKIE = "pedeai_refresh";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ownerBuildsTheTeamAndEachRoleSeesOnlyWhatItShould() throws Exception {
        String suffix = uniqueSuffix();
        Session owner = register("Pizzaria " + suffix, "Ana", "ana-" + suffix + "@example.com");

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("OWNER"))
                .andExpect(jsonPath("$.store.name").value("Pizzaria " + suffix));

        String cashierEmail = "caio-" + suffix + "@example.com";
        mockMvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Caio","email":"%s","password":"senha-do-caio","role":"CASHIER"}
                                """.formatted(cashierEmail)))
                .andExpect(status().isCreated());

        Session cashier = login(cashierEmail, "senha-do-caio");
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, cashier.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/store").header(HttpHeaders.AUTHORIZATION, cashier.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pizzaria " + suffix));
    }

    @Test
    void refreshTokensRotateAndReuseRevokesEverySession() throws Exception {
        String suffix = uniqueSuffix();
        Session first = register("Lanchonete " + suffix, "Bia", "bia-" + suffix + "@example.com");

        Session second = refresh(first.refreshToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());

        // O token antigo já foi trocado: usá-lo de novo indica cópia, e todas as sessões caem.
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, first.refreshToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, second.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        String suffix = uniqueSuffix();
        Session session = register("Bar " + suffix, "Rui", "rui-" + suffix + "@example.com");

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(REFRESH_COOKIE, session.refreshToken())))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, session.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oneStoreCannotReachAnotherStoresUsers() throws Exception {
        String suffix = uniqueSuffix();
        Session storeA = register("Loja A " + suffix, "Ana", "a-" + suffix + "@example.com");
        Session storeB = register("Loja B " + suffix, "Beto", "b-" + suffix + "@example.com");
        String ownerOfA = userIdOf(storeA);

        mockMvc.perform(get("/api/users/{id}", ownerOfA).header(HttpHeaders.AUTHORIZATION, storeB.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/users/{id}", ownerOfA).header(HttpHeaders.AUTHORIZATION, storeB.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active":false}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, storeB.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Beto"));
    }

    @Test
    void theLastOwnerCannotBeDemoted() throws Exception {
        String suffix = uniqueSuffix();
        Session owner = register("Cantina " + suffix, "Lia", "lia-" + suffix + "@example.com");

        mockMvc.perform(patch("/api/users/{id}", userIdOf(owner)).header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"MANAGER"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A loja precisa de pelo menos um dono ativo."));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String suffix = uniqueSuffix();
        register("Primeira " + suffix, "Ana", "dup-" + suffix + "@example.com");

        mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Segunda " + suffix, "Ana", "DUP-" + suffix + "@example.com")))
                .andExpect(status().isConflict());
    }

    private Session register(String storeName, String ownerName, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(storeName, ownerName, email)))
                .andExpect(status().isCreated())
                .andReturn();
        return session(result);
    }

    private Session login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return session(result);
    }

    private Session refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, refreshToken)))
                .andExpect(status().isOk())
                .andReturn();
        return session(result);
    }

    private String userIdOf(Session session) throws Exception {
        String body = mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.user.id");
    }

    private static Session session(MvcResult result) throws Exception {
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("HttpOnly").contains("SameSite=Strict");
        String refreshToken = setCookie.substring(REFRESH_COOKIE.length() + 1, setCookie.indexOf(';'));
        return new Session(accessToken, refreshToken);
    }

    private static String registerBody(String storeName, String ownerName, String email) {
        return """
                {"storeName":"%s","ownerName":"%s","email":"%s","password":"senha-forte-1"}
                """.formatted(storeName, ownerName, email);
    }

    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Session(String accessToken, String refreshToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }
}
