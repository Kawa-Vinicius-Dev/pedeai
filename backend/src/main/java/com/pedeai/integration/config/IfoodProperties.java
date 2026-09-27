package com.pedeai.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Credenciais do aplicativo centralizado do PedeAí no iFood Developer, só por variável de ambiente. Os caminhos do
 * polling e do acknowledgment são configuráveis porque a documentação mostra versões diferentes deles: confirmar na
 * homologação (docs/05-integracoes.md#pendências-para-validar-na-documentação-oficial).
 *
 * @param simulator libera a injeção de pedidos simulados, para desenvolver e demonstrar sem credenciais
 */
@ConfigurationProperties("app.ifood")
public record IfoodProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://merchant-api.ifood.com.br") String baseUrl,
        String clientId,
        String clientSecret,
        @DefaultValue("/order/v1.0/orders:polling") String pollingPath,
        @DefaultValue("/order/v1.0/orders:acknowledgment") String acknowledgmentPath,
        @DefaultValue("false") boolean simulator
) {
    /** Ligado e com credenciais: só então o PedeAí conversa com o iFood. */
    public boolean configured() {
        return enabled && clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
