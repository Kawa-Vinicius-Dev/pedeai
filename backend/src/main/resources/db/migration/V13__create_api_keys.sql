-- API de pedidos para sistemas de terceiros (docs/05-integracoes.md#api-de-pedidos-do-pedeaí).

-- A chave só aparece uma vez, na criação. Aqui fica o SHA-256 dela; o prefixo serve para a loja reconhecer qual é.
CREATE TABLE api_key (
    id           UUID        PRIMARY KEY,
    store_id     UUID        NOT NULL REFERENCES store (id),
    name         VARCHAR(60) NOT NULL,
    key_prefix   VARCHAR(16) NOT NULL,
    key_hash     VARCHAR(64) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_api_key_hash UNIQUE (key_hash)
);

CREATE INDEX ix_api_key_store ON api_key (store_id);

-- Pedido criado pela API: origem própria, com o id do sistema de origem em external_id (o UNIQUE de
-- (store_id, source, external_id) já impede o mesmo pedido duas vezes).
ALTER TABLE orders DROP CONSTRAINT ck_orders_source;
ALTER TABLE orders ADD CONSTRAINT ck_orders_source
    CHECK (source IN ('PEDEAI', 'DIGITAL_MENU', 'API', 'IFOOD', 'NINETY_NINE_FOOD'));
