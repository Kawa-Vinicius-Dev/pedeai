package com.pedeai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@ConfigurationProperties("app")
public record AppProperties(Auth auth, boolean signupEnabled, Integer signupsPerAddressPerHour,
                            List<String> corsAllowedOrigins) {
    private static final int DEFAULT_SIGNUPS_PER_ADDRESS = 5;


    public AppProperties {
        Objects.requireNonNull(auth, "Configure app.auth.");
        signupsPerAddressPerHour = signupsPerAddressPerHour == null ? DEFAULT_SIGNUPS_PER_ADDRESS
                : signupsPerAddressPerHour;
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
    }

    public record Auth(
            String jwtSecret,
            String jwtIssuer,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            Duration refreshReuseGrace,
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
            Objects.requireNonNull(refreshReuseGrace, "Configure app.auth.refresh-reuse-grace.");
            Objects.requireNonNull(refreshCookieName, "Configure app.auth.refresh-cookie-name.");
        }
    }
}
