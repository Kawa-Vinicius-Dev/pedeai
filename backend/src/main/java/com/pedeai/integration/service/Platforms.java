package com.pedeai.integration.service;

import com.pedeai.integration.config.IfoodProperties;
import com.pedeai.integration.config.OpenDeliveryProperties;
import com.pedeai.order.domain.OrderSource;
import org.springframework.stereotype.Component;

/**
 * O estado de cada plataforma num lugar só: o iFood pela integração própria, a 99Food e outro app compatível pelo
 * padrão Open Delivery. Sem credenciais, o simulador faz o papel da plataforma.
 */
@Component
public class Platforms {
    private final IfoodProperties ifood;
    private final OpenDeliveryProperties openDelivery;

    public Platforms(IfoodProperties ifood, OpenDeliveryProperties openDelivery) {
        this.ifood = ifood;
        this.openDelivery = openDelivery;
    }

    /** Com credenciais: o PedeAí conversa com a plataforma de verdade. */
    public boolean configured(OrderSource provider) {
        return provider == OrderSource.IFOOD ? ifood.configured() : openDelivery.configured(provider);
    }

    public boolean simulator(OrderSource provider) {
        return provider == OrderSource.IFOOD ? ifood.simulator() : openDelivery.simulator();
    }

    /** Sem credenciais e com o simulador: a plataforma é de mentira. */
    public boolean simulated(OrderSource provider) {
        return !configured(provider) && simulator(provider);
    }

    public boolean available(OrderSource provider) {
        return provider.isMarketplace() && (configured(provider) || simulator(provider));
    }

    /** "iFood", "99Food" ou o nome do app Open Delivery. */
    public String label(OrderSource provider) {
        return provider == OrderSource.IFOOD ? "iFood" : openDelivery.label(provider);
    }
}
