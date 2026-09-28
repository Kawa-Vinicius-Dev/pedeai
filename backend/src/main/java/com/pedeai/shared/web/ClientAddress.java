package com.pedeai.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * De onde veio a requisição, para os limites de tentativa. Atrás de proxy (Vercel, Caddy), o IP da conexão é o do
 * proxy, e o do cliente vem no primeiro item de X-Forwarded-For. Esse cabeçalho pode ser forjado por quem chama a API
 * direto: por isso nenhum limite depende só dele (login também limita por e-mail; pareamento tem teto global).
 */
public final class ClientAddress {
    private ClientAddress() {
    }

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty() && first.length() <= 45) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
