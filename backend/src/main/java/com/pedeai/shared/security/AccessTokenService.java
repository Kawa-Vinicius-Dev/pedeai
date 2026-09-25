package com.pedeai.shared.security;

import com.pedeai.shared.config.AppProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccessTokenService {
    private final JwtEncoder jwtEncoder;
    private final AppProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder jwtEncoder, AppProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issue(UUID userId, UUID storeId, Role role, String name) {
        Instant now = Instant.now(clock);
        Duration ttl = properties.auth().accessTokenTtl();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.auth().jwtIssuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(CurrentUser.STORE_CLAIM, storeId.toString())
                .claim(CurrentUser.ROLE_CLAIM, role.name())
                .claim(CurrentUser.NAME_CLAIM, name)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, ttl.toSeconds());
    }

    public record IssuedAccessToken(String value, long expiresInSeconds) {
    }
}
