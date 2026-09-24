package com.pedeai.store.controller;

import com.pedeai.shared.config.TimeConfig;
import com.pedeai.shared.exception.InvalidCredentialsException;
import com.pedeai.shared.security.Role;
import com.pedeai.shared.security.SecurityConfig;
import com.pedeai.store.dto.AuthResponse;
import com.pedeai.store.dto.LoginRequest;
import com.pedeai.store.dto.StoreSummaryResponse;
import com.pedeai.store.dto.UserResponse;
import com.pedeai.store.service.AuthResult;
import com.pedeai.store.service.AuthService;
import com.pedeai.store.service.IssuedRefreshToken;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static com.pedeai.support.TestSecurity.USER_ID;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, TimeConfig.class, RefreshTokenCookies.class})
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void loginReturnsAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
        when(authService.login(eq(new LoginRequest("ana@example.com", "senha-forte-1")), eq("Chrome")))
                .thenReturn(result());

        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.USER_AGENT, "Chrome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@example.com","password":"senha-forte-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.role").value("OWNER"))
                .andExpect(jsonPath("$.store.name").value("Pizzaria Bella"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("pedeai_refresh=refresh-value"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/api/auth"))));
    }

    @Test
    void loginValidatesTheBody() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nao-e-email","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(jsonPath("$.fields.email").value("E-mail inválido."))
                .andExpect(jsonPath("$.fields.password").value("Informe a senha."));
    }

    @Test
    void loginReturns401ForInvalidCredentials() throws Exception {
        when(authService.login(any(), any())).thenThrow(new InvalidCredentialsException("E-mail ou senha inválidos."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@example.com","password":"errada-123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos."));
    }

    @Test
    void refreshUsesTheCookieValue() throws Exception {
        when(authService.refresh(eq("cookie-antigo"), isNull())).thenReturn(result());

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("pedeai_refresh", "cookie-antigo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("pedeai_refresh=refresh-value")));
    }

    @Test
    void refreshWithoutCookieReturns401() throws Exception {
        when(authService.refresh(isNull(), isNull()))
                .thenThrow(new InvalidCredentialsException("Sua sessão expirou. Faça login novamente."));

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sua sessão expirou. Faça login novamente."));
    }

    @Test
    void expiredBearerTokenDoesNotBlockTheRefreshEndpoint() throws Exception {
        when(authService.refresh(eq("cookie"), isNull())).thenReturn(result());

        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token.vencido.qualquer")
                        .cookie(new Cookie("pedeai_refresh", "cookie")))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRevokesAndClearsTheCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("pedeai_refresh", "cookie")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("pedeai_refresh="),
                        containsString("Max-Age=0"))));
        verify(authService).logout("cookie");
    }

    private static AuthResult result() {
        AuthResponse response = new AuthResponse("jwt", "Bearer", 900,
                new UserResponse(USER_ID, "Ana", "ana@example.com", Role.OWNER, true, NOW),
                new StoreSummaryResponse(STORE_ID, "Pizzaria Bella"));
        return new AuthResult(response, new IssuedRefreshToken("refresh-value", NOW.plusSeconds(3600)));
    }
}
