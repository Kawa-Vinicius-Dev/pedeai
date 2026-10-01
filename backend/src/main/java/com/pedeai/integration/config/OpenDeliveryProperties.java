package com.pedeai.integration.config;

import com.pedeai.order.domain.OrderSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Optional;

/**
 * Apps de pedido no padrão Open Delivery (docs/05-integracoes.md#open-delivery), só por variável de ambiente. A 99Food
 * é um deles ({@code provider = NINETY_NINE_FOOD}); outro app compatível entra como {@code OPEN_DELIVERY}.
 *
 * @param simulator libera pedidos simulados desses apps, para desenvolver e demonstrar sem credenciais
 */
@ConfigurationProperties("app.opendelivery")
public record OpenDeliveryProperties(
        @DefaultValue("false") boolean simulator,
        @DefaultValue List<App> apps
) {
    /**
     * Um app de pedidos. {@code appId}: o identificador que o app manda no header {@code X-App-Id} do webhook.
     *
     * @param pollingPath confirmar com o app (a especificação usa {@code /v1/events:polling})
     */
    public record App(
            OrderSource provider,
            String name,
            String baseUrl,
            String clientId,
            String clientSecret,
            String appId,
            @DefaultValue("/v1/events:polling") String pollingPath
    ) {
        public boolean configured() {
            return provider != null && filled(baseUrl) && filled(clientId) && filled(clientSecret);
        }

        private static boolean filled(String text) {
            return text != null && !text.isBlank();
        }
    }

    /** ponytail: um app por provider; mais de um app genérico pede um app_id no vínculo da loja. */
    public Optional<App> app(OrderSource provider) {
        return apps.stream().filter(app -> app.provider() == provider && app.configured()).findFirst();
    }

    public Optional<App> appById(String appId) {
        return apps.stream().filter(app -> app.configured() && appId != null && appId.equals(app.appId()))
                .findFirst();
    }

    public boolean configured(OrderSource provider) {
        return app(provider).isPresent();
    }

    /** "99Food", ou o nome configurado do app genérico. */
    public String label(OrderSource provider) {
        return app(provider).map(App::name).filter(name -> !name.isBlank())
                .orElse(provider == OrderSource.NINETY_NINE_FOOD ? "99Food" : "Open Delivery");
    }
}
