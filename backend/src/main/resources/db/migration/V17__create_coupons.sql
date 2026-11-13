-- Cupons de desconto do cardápio digital (docs/08-mercado.md).
CREATE TABLE coupon (
    id              UUID        PRIMARY KEY,
    store_id        UUID        NOT NULL REFERENCES store (id),
    -- Sempre em maiúsculas e sem espaço: "BEMVINDO10".
    code            VARCHAR(30) NOT NULL,
    type            VARCHAR(10) NOT NULL,
    -- PERCENT: pontos-base (1000 = 10%). FIXED: centavos.
    amount          BIGINT      NOT NULL,
    min_order_cents BIGINT      NOT NULL,
    -- Nulo: sem limite de usos.
    max_uses        INTEGER,
    used_count      INTEGER     NOT NULL,
    -- Nulo: sem validade. Vale até o fim deste dia, no fuso da loja.
    valid_until     DATE,
    active          BOOLEAN     NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT      NOT NULL,
    CONSTRAINT uk_coupon_code UNIQUE (store_id, code),
    CONSTRAINT ck_coupon_type CHECK (type IN ('PERCENT', 'FIXED')),
    CONSTRAINT ck_coupon_amount CHECK (amount > 0 AND min_order_cents >= 0 AND used_count >= 0)
);
