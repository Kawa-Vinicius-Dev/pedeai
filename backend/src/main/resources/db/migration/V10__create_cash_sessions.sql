-- Caixa (docs/03-modelo-de-dados.md#pagamentos-e-caixa-payment): abertura, sangria, suprimento e fechamento.

CREATE TABLE cash_session (
    id                   UUID         PRIMARY KEY,
    store_id             UUID         NOT NULL REFERENCES store (id),
    status               VARCHAR(10)  NOT NULL,
    -- Igual a store_id só enquanto aberto: o UNIQUE barra dois caixas abertos na mesma loja, até em cliques
    -- simultâneos (o H2 dos testes não tem índice parcial).
    open_store_id        UUID,
    opened_by            UUID         NOT NULL,
    opened_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    opening_amount_cents BIGINT       NOT NULL,
    closed_by            UUID,
    closed_at            TIMESTAMP WITH TIME ZONE,
    notes                VARCHAR(300),
    version              BIGINT       NOT NULL,
    CONSTRAINT uk_cash_session_open UNIQUE (open_store_id),
    CONSTRAINT ck_cash_session_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_cash_session_open CHECK ((status = 'OPEN' AND open_store_id IS NOT NULL AND closed_at IS NULL)
        OR (status = 'CLOSED' AND open_store_id IS NULL AND closed_at IS NOT NULL)),
    CONSTRAINT ck_cash_session_opening CHECK (opening_amount_cents >= 0)
);

CREATE INDEX ix_cash_session_store ON cash_session (store_id, opened_at);

-- Sangria (WITHDRAWAL) tira dinheiro da gaveta; suprimento (DEPOSIT) coloca.
CREATE TABLE cash_movement (
    id              UUID         PRIMARY KEY,
    store_id        UUID         NOT NULL REFERENCES store (id),
    cash_session_id UUID         NOT NULL REFERENCES cash_session (id),
    type            VARCHAR(10)  NOT NULL,
    amount_cents    BIGINT       NOT NULL,
    reason          VARCHAR(160) NOT NULL,
    created_by      UUID         NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_cash_movement_type CHECK (type IN ('WITHDRAWAL', 'DEPOSIT')),
    CONSTRAINT ck_cash_movement_amount CHECK (amount_cents > 0)
);

CREATE INDEX ix_cash_movement_session ON cash_movement (cash_session_id);

-- Conferência do fechamento: o esperado fica gravado como estava na hora, para o relatório não mudar depois.
CREATE TABLE cash_session_count (
    cash_session_id   UUID   NOT NULL REFERENCES cash_session (id),
    payment_method_id UUID   NOT NULL REFERENCES payment_method (id),
    expected_cents    BIGINT NOT NULL,
    counted_cents     BIGINT NOT NULL,
    CONSTRAINT pk_cash_session_count PRIMARY KEY (cash_session_id, payment_method_id),
    CONSTRAINT ck_cash_session_count CHECK (counted_cents >= 0)
);

-- Relatório de fechamento de caixa na impressora térmica.
ALTER TABLE print_job DROP CONSTRAINT ck_print_job_document;
ALTER TABLE print_job ADD CONSTRAINT ck_print_job_document
    CHECK (document_type IN ('PRODUCTION_TICKET', 'ORDER_TICKET', 'CANCELLATION_TICKET', 'CASH_REPORT'));
