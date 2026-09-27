-- Fila de impressão. O banco é a fila: o trabalho nasce na mesma transação que confirma o pedido.
-- Ver docs/04-impressao.md#ciclo-de-vida-de-um-trabalho.
CREATE TABLE print_job (
    id              UUID         PRIMARY KEY,
    store_id        UUID         NOT NULL REFERENCES store (id),
    printer_id      UUID         NOT NULL REFERENCES printer (id),
    agent_id        UUID         NOT NULL REFERENCES print_agent (id),
    document_type   VARCHAR(30)  NOT NULL,
    order_id        UUID         REFERENCES orders (id),
    sector_id       UUID         REFERENCES sector (id),
    reason          VARCHAR(10)  NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    delivery_key    VARCHAR(64)  NOT NULL,
    status          VARCHAR(10)  NOT NULL,
    attempts        INTEGER      NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_until     TIMESTAMP WITH TIME ZONE,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error      VARCHAR(255),
    payload         BYTEA        NOT NULL,
    preview         TEXT         NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at         TIMESTAMP WITH TIME ZONE,
    printed_at      TIMESTAMP WITH TIME ZONE,
    version         BIGINT       NOT NULL,
    CONSTRAINT uk_print_job_idempotency UNIQUE (store_id, idempotency_key),
    CONSTRAINT uk_print_job_delivery UNIQUE (delivery_key),
    CONSTRAINT ck_print_job_document CHECK (document_type IN ('PRODUCTION_TICKET', 'ORDER_TICKET')),
    CONSTRAINT ck_print_job_reason CHECK (reason IN ('AUTO', 'MANUAL', 'REPRINT', 'TEST')),
    CONSTRAINT ck_print_job_status CHECK (status IN ('PENDING', 'SENT', 'PRINTED', 'FAILED', 'UNCERTAIN',
                                                     'EXPIRED', 'CANCELLED'))
);

CREATE INDEX ix_print_job_agent_status ON print_job (agent_id, status);
CREATE INDEX ix_print_job_order ON print_job (order_id);
CREATE INDEX ix_print_job_store_status ON print_job (store_id, status);
