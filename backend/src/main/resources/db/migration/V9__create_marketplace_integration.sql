-- Integração com marketplaces (docs/05-integracoes.md): vínculo da loja, inbox de eventos e outbox de ações.

-- Um merchant do iFood só pode estar ligado a uma loja.
CREATE TABLE marketplace_connection (
    id                   UUID         PRIMARY KEY,
    store_id             UUID         NOT NULL REFERENCES store (id),
    provider             VARCHAR(20)  NOT NULL,
    external_merchant_id VARCHAR(80)  NOT NULL,
    merchant_name        VARCHAR(160),
    status               VARCHAR(10)  NOT NULL,
    auto_confirm         BOOLEAN      NOT NULL,
    last_event_at        TIMESTAMP WITH TIME ZONE,
    last_error           VARCHAR(300),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    version              BIGINT       NOT NULL,
    CONSTRAINT uk_marketplace_connection_merchant UNIQUE (provider, external_merchant_id),
    CONSTRAINT ck_marketplace_connection_provider CHECK (provider IN ('IFOOD', 'NINETY_NINE_FOOD')),
    CONSTRAINT ck_marketplace_connection_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ERROR'))
);

CREATE INDEX ix_marketplace_connection_store ON marketplace_connection (store_id);

-- Todo evento recebido é gravado antes de ser processado. O mesmo evento chegando de novo é gravado uma vez.
CREATE TABLE inbound_event (
    id                   UUID         PRIMARY KEY,
    provider             VARCHAR(20)  NOT NULL,
    external_event_id    VARCHAR(80)  NOT NULL,
    external_merchant_id VARCHAR(80),
    external_order_id    VARCHAR(80),
    event_code           VARCHAR(40)  NOT NULL,
    payload              TEXT         NOT NULL,
    store_id             UUID         REFERENCES store (id),
    status               VARCHAR(10)  NOT NULL,
    attempts             INTEGER      NOT NULL,
    next_attempt_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error           VARCHAR(300),
    received_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at         TIMESTAMP WITH TIME ZONE,
    version              BIGINT       NOT NULL,
    CONSTRAINT uk_inbound_event UNIQUE (provider, external_event_id),
    CONSTRAINT ck_inbound_event_status CHECK (status IN ('PENDING', 'PROCESSED', 'IGNORED', 'FAILED'))
);

CREATE INDEX ix_inbound_event_status ON inbound_event (status, next_attempt_at);

-- Ação a enviar para a plataforma, gravada na mesma transação da mudança de status. Sai em ordem por pedido.
CREATE TABLE outbound_action (
    id                UUID         PRIMARY KEY,
    store_id          UUID         NOT NULL REFERENCES store (id),
    provider          VARCHAR(20)  NOT NULL,
    order_id          UUID         NOT NULL REFERENCES orders (id),
    external_order_id VARCHAR(80)  NOT NULL,
    action            VARCHAR(30)  NOT NULL,
    payload           TEXT,
    status            VARCHAR(10)  NOT NULL,
    attempts          INTEGER      NOT NULL,
    next_attempt_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error        VARCHAR(300),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    done_at           TIMESTAMP WITH TIME ZONE,
    version           BIGINT       NOT NULL,
    CONSTRAINT ck_outbound_action_action CHECK (action IN ('CONFIRM', 'START_PREPARATION', 'READY', 'DISPATCH',
                                                           'REQUEST_CANCELLATION')),
    CONSTRAINT ck_outbound_action_status CHECK (status IN ('PENDING', 'DONE', 'FAILED', 'SKIPPED'))
);

CREATE INDEX ix_outbound_action_status ON outbound_action (status, next_attempt_at);
CREATE INDEX ix_outbound_action_order ON outbound_action (order_id);
