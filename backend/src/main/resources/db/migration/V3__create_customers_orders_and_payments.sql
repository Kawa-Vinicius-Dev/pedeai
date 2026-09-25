-- Clientes dos canais próprios (balcão, telefone, WhatsApp). Cliente de marketplace fica só no pedido.
CREATE TABLE customer (
    id         UUID         PRIMARY KEY,
    store_id   UUID         NOT NULL REFERENCES store (id),
    name       VARCHAR(120) NOT NULL,
    -- E.164: +5511999990000
    phone      VARCHAR(20)  NOT NULL,
    email      VARCHAR(254),
    notes      VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version    BIGINT       NOT NULL,
    CONSTRAINT uk_customer_phone UNIQUE (store_id, phone)
);

CREATE TABLE customer_address (
    id           UUID         PRIMARY KEY,
    store_id     UUID         NOT NULL REFERENCES store (id),
    customer_id  UUID         NOT NULL REFERENCES customer (id),
    label        VARCHAR(40),
    street       VARCHAR(120) NOT NULL,
    number       VARCHAR(20)  NOT NULL,
    complement   VARCHAR(80),
    neighborhood VARCHAR(80)  NOT NULL,
    city         VARCHAR(80),
    state        VARCHAR(40),
    postal_code  VARCHAR(10),
    reference    VARCHAR(160),
    active       BOOLEAN      NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    version      BIGINT       NOT NULL
);

CREATE INDEX ix_customer_address_customer ON customer_address (customer_id);

-- Taxa de entrega por bairro. A chave é o nome sem acento e em minúsculas, para "Centro" e "centro" serem o mesmo.
CREATE TABLE delivery_zone (
    id               UUID        PRIMARY KEY,
    store_id         UUID        NOT NULL REFERENCES store (id),
    neighborhood     VARCHAR(80) NOT NULL,
    neighborhood_key VARCHAR(80) NOT NULL,
    fee_cents        BIGINT      NOT NULL,
    active           BOOLEAN     NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    version          BIGINT      NOT NULL,
    CONSTRAINT ck_delivery_zone_fee CHECK (fee_cents >= 0),
    CONSTRAINT uk_delivery_zone_key UNIQUE (store_id, neighborhood_key)
);

CREATE TABLE payment_method (
    id         UUID        PRIMARY KEY,
    store_id   UUID        NOT NULL REFERENCES store (id),
    name       VARCHAR(60) NOT NULL,
    type       VARCHAR(10) NOT NULL,
    active     BOOLEAN     NOT NULL,
    sort_order INTEGER     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version    BIGINT      NOT NULL,
    CONSTRAINT ck_payment_method_type CHECK (type IN ('CASH', 'PIX', 'CREDIT', 'DEBIT', 'VOUCHER', 'ONLINE', 'OTHER'))
);

CREATE INDEX ix_payment_method_store ON payment_method (store_id);

-- "order" é palavra reservada, por isso orders.
CREATE TABLE orders (
    id                     UUID         PRIMARY KEY,
    store_id               UUID         NOT NULL REFERENCES store (id),
    business_date          DATE         NOT NULL,
    number                 INTEGER      NOT NULL,
    type                   VARCHAR(10)  NOT NULL,
    source                 VARCHAR(20)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    customer_id            UUID         REFERENCES customer (id),
    customer_name          VARCHAR(120),
    customer_phone         VARCHAR(20),
    delivery_street        VARCHAR(120),
    delivery_number        VARCHAR(20),
    delivery_complement    VARCHAR(80),
    delivery_neighborhood  VARCHAR(80),
    delivery_city          VARCHAR(80),
    delivery_state         VARCHAR(40),
    delivery_postal_code   VARCHAR(10),
    delivery_reference     VARCHAR(160),
    notes                  VARCHAR(500),
    subtotal_cents         BIGINT       NOT NULL,
    discount_cents         BIGINT       NOT NULL,
    platform_subsidy_cents BIGINT       NOT NULL,
    delivery_fee_cents     BIGINT       NOT NULL,
    additional_fee_cents   BIGINT       NOT NULL,
    total_cents            BIGINT       NOT NULL,
    external_id            VARCHAR(80),
    external_display_id    VARCHAR(40),
    scheduled_for          TIMESTAMP WITH TIME ZONE,
    confirmed_at           TIMESTAMP WITH TIME ZONE,
    preparation_started_at TIMESTAMP WITH TIME ZONE,
    ready_at               TIMESTAMP WITH TIME ZONE,
    dispatched_at          TIMESTAMP WITH TIME ZONE,
    completed_at           TIMESTAMP WITH TIME ZONE,
    cancelled_at           TIMESTAMP WITH TIME ZONE,
    cancel_reason          VARCHAR(300),
    created_by             UUID,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    version                BIGINT       NOT NULL,
    CONSTRAINT ck_orders_type CHECK (type IN ('TAKEOUT', 'DELIVERY', 'DINE_IN')),
    CONSTRAINT ck_orders_source CHECK (source IN ('PEDEAI', 'IFOOD', 'NINETY_NINE_FOOD')),
    CONSTRAINT ck_orders_status CHECK (status IN ('RECEIVED', 'CONFIRMED', 'IN_PREPARATION', 'READY', 'DISPATCHED',
                                                  'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_orders_amounts CHECK (subtotal_cents >= 0 AND discount_cents >= 0 AND platform_subsidy_cents >= 0
        AND delivery_fee_cents >= 0 AND additional_fee_cents >= 0 AND total_cents >= 0),
    CONSTRAINT uk_orders_number UNIQUE (store_id, business_date, number),
    -- Barra importar o mesmo pedido de marketplace duas vezes. Pedidos próprios têm external_id nulo.
    CONSTRAINT uk_orders_external UNIQUE (store_id, source, external_id)
);

CREATE INDEX ix_orders_store_status ON orders (store_id, status);
CREATE INDEX ix_orders_store_date ON orders (store_id, business_date);

-- Itens e opções são cópia do cardápio na hora do pedido: mudar o cardápio não altera pedido antigo.
CREATE TABLE order_item (
    id                  UUID         PRIMARY KEY,
    store_id            UUID         NOT NULL REFERENCES store (id),
    order_id            UUID         NOT NULL REFERENCES orders (id),
    product_id          UUID         REFERENCES product (id),
    code                VARCHAR(40),
    name                VARCHAR(120) NOT NULL,
    sector_id           UUID         REFERENCES sector (id),
    quantity            INTEGER      NOT NULL,
    unit_price_cents    BIGINT       NOT NULL,
    -- O que as opções somam a uma unidade, já pela regra de cada grupo (soma, maior valor ou média).
    options_price_cents BIGINT       NOT NULL,
    total_cents         BIGINT       NOT NULL,
    notes               VARCHAR(300),
    status              VARCHAR(10)  NOT NULL,
    cancel_reason       VARCHAR(300),
    sort_order          INTEGER      NOT NULL,
    CONSTRAINT ck_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_item_amounts CHECK (unit_price_cents >= 0 AND options_price_cents >= 0 AND total_cents >= 0),
    CONSTRAINT ck_order_item_status CHECK (status IN ('ACTIVE', 'CANCELLED'))
);

CREATE INDEX ix_order_item_order ON order_item (order_id);

-- Preço de cada opção no cardápio. O valor cobrado fica no item, porque em maior valor e média ele não é a soma.
CREATE TABLE order_item_option (
    id               UUID        PRIMARY KEY,
    store_id         UUID        NOT NULL REFERENCES store (id),
    order_item_id    UUID        NOT NULL REFERENCES order_item (id),
    option_item_id   UUID        REFERENCES option_item (id),
    group_name       VARCHAR(80) NOT NULL,
    name             VARCHAR(80) NOT NULL,
    code             VARCHAR(40),
    quantity         INTEGER     NOT NULL,
    unit_price_cents BIGINT      NOT NULL,
    sort_order       INTEGER     NOT NULL,
    CONSTRAINT ck_order_item_option_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_item_option_price CHECK (unit_price_cents >= 0)
);

CREATE INDEX ix_order_item_option_item ON order_item_option (order_item_id);

-- Linha do tempo do pedido. Só inserção.
CREATE TABLE order_status_history (
    id          UUID         PRIMARY KEY,
    store_id    UUID         NOT NULL REFERENCES store (id),
    order_id    UUID         NOT NULL REFERENCES orders (id),
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    actor_type  VARCHAR(12)  NOT NULL,
    actor_id    UUID,
    actor_name  VARCHAR(120),
    reason      VARCHAR(300),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_order_status_history_actor CHECK (actor_type IN ('USER', 'SYSTEM', 'MARKETPLACE'))
);

CREATE INDEX ix_order_status_history_order ON order_status_history (order_id);

-- Número curto do pedido ("Pedido 42"), que recomeça a cada dia operacional.
CREATE TABLE order_number_counter (
    store_id      UUID    NOT NULL REFERENCES store (id),
    business_date DATE    NOT NULL,
    last_number   INTEGER NOT NULL,
    PRIMARY KEY (store_id, business_date)
);

CREATE TABLE payment (
    id                UUID        PRIMARY KEY,
    store_id          UUID        NOT NULL REFERENCES store (id),
    order_id          UUID        NOT NULL REFERENCES orders (id),
    payment_method_id UUID        NOT NULL REFERENCES payment_method (id),
    amount_cents      BIGINT      NOT NULL,
    -- "Troco para R$ 100": só em dinheiro.
    change_for_cents  BIGINT,
    status            VARCHAR(10) NOT NULL,
    origin            VARCHAR(12) NOT NULL,
    paid_at           TIMESTAMP WITH TIME ZONE,
    received_by       UUID,
    created_by        UUID,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    version           BIGINT      NOT NULL,
    CONSTRAINT ck_payment_amount CHECK (amount_cents > 0),
    CONSTRAINT ck_payment_change CHECK (change_for_cents IS NULL OR change_for_cents >= amount_cents),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'PAID', 'CANCELLED')),
    CONSTRAINT ck_payment_origin CHECK (origin IN ('LOCAL', 'MARKETPLACE'))
);

CREATE INDEX ix_payment_order ON payment (store_id, order_id);
