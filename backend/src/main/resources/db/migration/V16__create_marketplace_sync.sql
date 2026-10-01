-- O PedeAí manda no iFood (docs/05-integracoes.md#sincronização-com-o-ifood): cardápio, pausa da loja e horário.

-- Produto: vende ou não no iFood, preço fixo opcional lá, e a foto (a URL pública e o caminho que o iFood devolve).
ALTER TABLE product ADD COLUMN sell_on_ifood BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE product ADD COLUMN ifood_price_cents BIGINT;
ALTER TABLE product ADD CONSTRAINT ck_product_ifood_price CHECK (ifood_price_cents IS NULL OR ifood_price_cents >= 0);
ALTER TABLE product ADD COLUMN image_url VARCHAR(500);
ALTER TABLE product ADD COLUMN ifood_image_path VARCHAR(300);

-- Acréscimo nos preços do iFood, em pontos-base (1500 = 15%), para cobrir a comissão.
ALTER TABLE store ADD COLUMN ifood_markup_bp INTEGER NOT NULL DEFAULT 0;
ALTER TABLE store ADD CONSTRAINT ck_store_ifood_markup CHECK (ifood_markup_bp BETWEEN 0 AND 10000);

-- No vínculo: se o cardápio do app vem do PedeAí, e a pausa aberta lá (para reabrir).
ALTER TABLE marketplace_connection ADD COLUMN catalog_sync BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE marketplace_connection ADD COLUMN interruption_id VARCHAR(80);

-- Fila de envio por loja. Cada linha diz o que mandar; o conteúdo é lido na hora do envio, então várias mudanças
-- seguidas no mesmo produto viram um envio só.
CREATE TABLE marketplace_sync (
    id              UUID         PRIMARY KEY,
    store_id        UUID         NOT NULL REFERENCES store (id),
    connection_id   UUID         NOT NULL REFERENCES marketplace_connection (id),
    kind            VARCHAR(20)  NOT NULL,
    reference_id    UUID,
    status          VARCHAR(10)  NOT NULL,
    attempts        INTEGER      NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error      VARCHAR(300),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    done_at         TIMESTAMP WITH TIME ZONE,
    version         BIGINT       NOT NULL,
    CONSTRAINT ck_marketplace_sync_kind CHECK (kind IN ('STORE_STATUS', 'OPENING_HOURS', 'ITEM')),
    CONSTRAINT ck_marketplace_sync_status CHECK (status IN ('PENDING', 'DONE', 'FAILED', 'SKIPPED'))
);

CREATE INDEX ix_marketplace_sync_due ON marketplace_sync (status, next_attempt_at);
CREATE INDEX ix_marketplace_sync_connection ON marketplace_sync (connection_id, status);

-- Categoria do PedeAí -> categoria criada no catálogo do app.
CREATE TABLE marketplace_category_link (
    connection_id UUID        NOT NULL REFERENCES marketplace_connection (id),
    category_id   UUID        NOT NULL REFERENCES category (id),
    external_id   VARCHAR(80) NOT NULL,
    CONSTRAINT pk_marketplace_category_link PRIMARY KEY (connection_id, category_id)
);
