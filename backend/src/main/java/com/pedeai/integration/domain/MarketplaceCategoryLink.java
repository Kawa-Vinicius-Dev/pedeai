package com.pedeai.integration.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.UUID;

/** A categoria do PedeAí já criada no catálogo do app, com o id que o app deu. */
@Entity
@Table(name = "marketplace_category_link")
public class MarketplaceCategoryLink {
    @Embeddable
    public record Key(UUID connectionId, UUID categoryId) implements Serializable {
    }

    @EmbeddedId
    private Key id;
    private String externalId;

    protected MarketplaceCategoryLink() {
    }

    public MarketplaceCategoryLink(UUID connectionId, UUID categoryId, String externalId) {
        this.id = new Key(connectionId, categoryId);
        this.externalId = externalId;
    }

    public String getExternalId() {
        return externalId;
    }
}
