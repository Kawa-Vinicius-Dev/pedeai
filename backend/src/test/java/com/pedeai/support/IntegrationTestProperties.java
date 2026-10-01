package com.pedeai.support;

import com.pedeai.integration.config.IfoodProperties;
import com.pedeai.integration.config.OpenDeliveryProperties;
import com.pedeai.integration.service.Platforms;

import java.util.List;

/** Propriedades das integrações para os testes, com os caminhos padrão da API de catálogo do iFood. */
public final class IntegrationTestProperties {
    private IntegrationTestProperties() {
    }

    public static IfoodProperties ifood(boolean enabled, String baseUrl, String clientId, String clientSecret,
                                        String pollingPath, String acknowledgmentPath, boolean simulator) {
        return new IfoodProperties(enabled, baseUrl, clientId, clientSecret, pollingPath, acknowledgmentPath,
                simulator, "/catalog/v2.0/merchants/{merchantId}/catalogs",
                "/catalog/v2.0/merchants/{merchantId}/catalogs/{catalogId}/categories?includeItems=true");
    }

    /** Só o iFood; nenhum app Open Delivery configurado. */
    public static Platforms platforms(IfoodProperties ifood) {
        return new Platforms(ifood, new OpenDeliveryProperties(false, List.of()));
    }
}
