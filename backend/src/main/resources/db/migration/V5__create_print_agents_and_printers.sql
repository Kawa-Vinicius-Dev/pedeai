-- Computador com o agente de impressão. O banco guarda só o hash do token de dispositivo.
-- Online ou offline sai de last_seen_at (heartbeat a cada 20 s).
CREATE TABLE print_agent (
    id           UUID         PRIMARY KEY,
    store_id     UUID         NOT NULL REFERENCES store (id),
    name         VARCHAR(80)  NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    os           VARCHAR(80),
    agent_version VARCHAR(40),
    last_seen_at TIMESTAMP WITH TIME ZONE,
    revoked_at   TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    version      BIGINT       NOT NULL,
    CONSTRAINT uk_print_agent_token UNIQUE (token_hash)
);

CREATE INDEX ix_print_agent_store ON print_agent (store_id);

-- Código de 6 dígitos digitado no agente. Vale 10 minutos e uma vez só.
CREATE TABLE agent_pairing_code (
    id         UUID        PRIMARY KEY,
    store_id   UUID        NOT NULL REFERENCES store (id),
    code_hash  VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version    BIGINT      NOT NULL
);

CREATE INDEX ix_agent_pairing_code_hash ON agent_pairing_code (code_hash);

-- Impressora térmica. Pertence a um agente, que manda os bytes pela rede (host:port) ou pelo Windows (system_name).
CREATE TABLE printer (
    id                UUID         PRIMARY KEY,
    store_id          UUID         NOT NULL REFERENCES store (id),
    agent_id          UUID         NOT NULL REFERENCES print_agent (id),
    name              VARCHAR(60)  NOT NULL,
    connection_type   VARCHAR(10)  NOT NULL,
    host              VARCHAR(255),
    port              INTEGER,
    system_name       VARCHAR(255),
    paper_width_mm    INTEGER      NOT NULL,
    columns           INTEGER      NOT NULL,
    codepage          VARCHAR(20)  NOT NULL,
    cut_mode          VARCHAR(10)  NOT NULL,
    active            BOOLEAN      NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    status_detail     VARCHAR(255),
    status_updated_at TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    version           BIGINT       NOT NULL,
    CONSTRAINT ck_printer_connection CHECK (
        (connection_type = 'NETWORK' AND host IS NOT NULL AND port BETWEEN 1 AND 65535)
        OR (connection_type = 'SYSTEM' AND system_name IS NOT NULL)),
    CONSTRAINT ck_printer_paper CHECK (paper_width_mm IN (58, 80)),
    CONSTRAINT ck_printer_columns CHECK (columns BETWEEN 24 AND 64),
    CONSTRAINT ck_printer_codepage CHECK (codepage IN ('PC437', 'PC850', 'PC860', 'WPC1252', 'PC858', 'NO_ACCENTS')),
    CONSTRAINT ck_printer_cut CHECK (cut_mode IN ('PARTIAL', 'FULL', 'NONE')),
    CONSTRAINT ck_printer_status CHECK (status IN ('UNKNOWN', 'ONLINE', 'OFFLINE', 'ERROR'))
);

CREATE INDEX ix_printer_store ON printer (store_id);
CREATE INDEX ix_printer_agent ON printer (agent_id);

-- Para qual impressora vai a produção de cada setor. A reserva recebe quando a principal está offline.
CREATE TABLE sector_printer (
    sector_id         UUID    PRIMARY KEY REFERENCES sector (id),
    store_id          UUID    NOT NULL REFERENCES store (id),
    printer_id        UUID    NOT NULL REFERENCES printer (id),
    backup_printer_id UUID    REFERENCES printer (id),
    copies            INTEGER NOT NULL,
    enabled           BOOLEAN NOT NULL,
    version           BIGINT  NOT NULL,
    CONSTRAINT ck_sector_printer_copies CHECK (copies BETWEEN 1 AND 5),
    CONSTRAINT ck_sector_printer_backup CHECK (backup_printer_id IS NULL OR backup_printer_id <> printer_id)
);

CREATE INDEX ix_sector_printer_store ON sector_printer (store_id);
