-- Entregadores da loja e quem levou cada pedido (docs/08-mercado.md).
CREATE TABLE courier (
    id         UUID         PRIMARY KEY,
    store_id   UUID         NOT NULL REFERENCES store (id),
    name       VARCHAR(120) NOT NULL,
    phone      VARCHAR(30),
    active     BOOLEAN      NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version    BIGINT       NOT NULL
);

CREATE INDEX ix_courier_store ON courier (store_id);

-- O nome fica copiado no pedido: renomear ou desativar o entregador não muda o histórico.
ALTER TABLE orders ADD COLUMN courier_id UUID REFERENCES courier (id);
ALTER TABLE orders ADD COLUMN courier_name VARCHAR(120);
