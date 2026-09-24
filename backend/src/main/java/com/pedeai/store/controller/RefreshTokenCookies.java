package com.pedeai.store.controller;

import com.pedeai.shared.config.AppProperties;
import com.pedeai.store.service.IssuedRefreshToken;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie do refresh token: HttpOnly (o JavaScript não lê), SameSite=Strict (não vai em requisição de
 * outro site) e restrito a /api/auth (só vai para login, renovação e logout).
 */
@Component
public class RefreshTokenCookies {
    static final String COOKIE_PATH = "/api/auth";

    private final AppProperties.Auth auth;

    public RefreshTokenCookies(AppProperties properties) {
        this.auth = properties.auth();
    }

    public String create(IssuedRefreshToken token) {
        return base(token.value()).maxAge(auth.refreshTokenTtl()).build().toString();
    }

    public String clear() {
        return base("").maxAge(0).build().toString();
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (auth.refreshCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(auth.refreshCookieName(), value)
                .httpOnly(true)
                .secure(auth.refreshCookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }
}
