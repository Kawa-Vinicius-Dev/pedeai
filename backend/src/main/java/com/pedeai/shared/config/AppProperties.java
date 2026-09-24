package com.pedeai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@ConfigurationProperties("app")
public record AppProperties(Auth auth, boolean signupEnabled, List<String> corsAllowedOrigins) {

    public AppProperties {
        Objects.requireNonNull(auth, "Configure app.auth.");
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
    }

    public record Auth(
            String jwtSecret,
            String jwtIssuer,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            String refreshCookieName,
            boolean refreshCookieSecure
    ) {
        private static final int MIN_SECRET_BYTES = 32;

        public Auth {
            if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 32 bytes.");
            }
            Objects.requireNonNull(jwtIssuer, "Configure app.auth.jwt-issuer.");
            Objects.requireNonNull(accessTokenTtl, "Configure app.auth.access-token-ttl.");
            Objects.requireNonNull(refreshTokenTtl, "Configure app.auth.refresh-token-ttl.");
            Objects.requireNonNull(refreshCookieName, "Configure app.auth.refresh-cookie-name.");
        }
    }
}
