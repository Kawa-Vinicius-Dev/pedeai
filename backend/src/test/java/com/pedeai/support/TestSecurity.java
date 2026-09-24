package com.pedeai.support;

import com.pedeai.shared.config.AppProperties;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Role;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public final class TestSecurity {
    public static final UUID STORE_ID = UUID.fromString("01a0d567-0000-7000-8000-000000000001");
    public static final UUID USER_ID = UUID.fromString("01a0d567-0000-7000-8000-000000000002");
    public static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private TestSecurity() {
    }

    /** Autentica a requisição do MockMvc como alguém da loja {@link #STORE_ID} com o papel informado. */
    public static RequestPostProcessor as(Role role) {
        return jwt()
                .jwt(token -> token
                        .subject(USER_ID.toString())
                        .claim(CurrentUser.STORE_CLAIM, STORE_ID.toString())
                        .claim(CurrentUser.ROLE_CLAIM, role.name())
                        .claim(CurrentUser.NAME_CLAIM, "Ana"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public static AppProperties properties(boolean signupEnabled) {
        return new AppProperties(
                new AppProperties.Auth("test-secret-with-at-least-thirty-two-bytes", "pedeai",
                        Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofSeconds(30), "pedeai_refresh", false),
                signupEnabled,
                List.of("http://localhost:5173"));
    }
}
