-- Setor de produção (Cozinha, Bar, Pizzaria). Um deles é o padrão da loja.
CREATE TABLE sector (
    id          UUID        PRIMARY KEY,
    store_id    UUID        NOT NULL REFERENCES store (id),
    name        VARCHAR(60) NOT NULL,
    is_default  BOOLEAN     NOT NULL,
    active      BOOLEAN     NOT NULL,
    sort_order  INTEGER     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT      NOT NULL
);

CREATE INDEX ix_sector_store ON sector (store_id);

CREATE TABLE category (
    id                UUID        PRIMARY KEY,
    store_id          UUID        NOT NULL REFERENCES store (id),
    name              VARCHAR(80) NOT NULL,
    default_sector_id UUID        REFERENCES sector (id),
    sort_order        INTEGER     NOT NULL,
    active            BOOLEAN     NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    version           BIGINT      NOT NULL
);

CREATE INDEX ix_category_store ON category (store_id);

-- Grupo de adicionais: "Adicionais" (soma), "Sabores" de pizza (maior valor ou média), "Ponto da carne".
CREATE TABLE option_group (
    id           UUID        PRIMARY KEY,
    store_id     UUID        NOT NULL REFERENCES store (id),
    name         VARCHAR(80) NOT NULL,
    min_choices  INTEGER     NOT NULL,
    max_choices  INTEGER     NOT NULL,
    pricing_rule VARCHAR(10) NOT NULL,
    active       BOOLEAN     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    version      BIGINT      NOT NULL,
    CONSTRAINT ck_option_group_choices CHECK (min_choices >= 0 AND max_choices >= 1 AND min_choices <= max_choices),
    CONSTRAINT ck_option_group_rule CHECK (pricing_rule IN ('SUM', 'MAX', 'AVERAGE'))
);

CREATE INDEX ix_option_group_store ON option_group (store_id);

-- "option" é palavra reservada em alguns bancos, por isso option_item.
CREATE TABLE option_item (
    id              UUID        PRIMARY KEY,
    store_id        UUID        NOT NULL REFERENCES store (id),
    option_group_id UUID        NOT NULL REFERENCES option_group (id),
    code            VARCHAR(40),
    name            VARCHAR(80) NOT NULL,
    price_cents     BIGINT      NOT NULL,
    available       BOOLEAN     NOT NULL,
    active          BOOLEAN     NOT NULL,
    sort_order      INTEGER     NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT      NOT NULL,
    CONSTRAINT ck_option_item_price CHECK (price_cents >= 0),
    CONSTRAINT uk_option_item_code UNIQUE (option_group_id, code)
);

CREATE INDEX ix_option_item_group ON option_item (option_group_id);

CREATE TABLE product (
    id          UUID         PRIMARY KEY,
    store_id    UUID         NOT NULL REFERENCES store (id),
    category_id UUID         NOT NULL REFERENCES category (id),
    code        VARCHAR(40),
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    price_cents BIGINT       NOT NULL,
    sector_id   UUID         REFERENCES sector (id),
    available   BOOLEAN      NOT NULL,
    active      BOOLEAN      NOT NULL,
    sort_order  INTEGER      NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT       NOT NULL,
    CONSTRAINT ck_product_price CHECK (price_cents >= 0),
    -- Código PDV: liga o item do iFood/99Food ao produto. Nulos não colidem.
    CONSTRAINT uk_product_code UNIQUE (store_id, code)
);

CREATE INDEX ix_product_store_category ON product (store_id, category_id);

-- Quais grupos de adicionais o produto oferece, e em que ordem aparecem.
CREATE TABLE product_option_group (
    product_id      UUID    NOT NULL REFERENCES product (id),
    option_group_id UUID    NOT NULL REFERENCES option_group (id),
    sort_order      INTEGER NOT NULL,
    PRIMARY KEY (product_id, sort_order)
);
