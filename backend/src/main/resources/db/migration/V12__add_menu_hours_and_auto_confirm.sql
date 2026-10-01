-- Cardápio digital: aceite automático e horário de funcionamento (docs/01-fluxos.md#fluxo-5--cardápio-digital-pedeaí).

ALTER TABLE store ADD COLUMN menu_auto_confirm BOOLEAN NOT NULL DEFAULT FALSE;

-- Um período por dia da semana (1 = segunda ... 7 = domingo). Fecha antes de abrir = passa da meia-noite.
CREATE TABLE store_opening_hours (
    store_id    UUID     NOT NULL REFERENCES store (id),
    day_of_week INTEGER  NOT NULL,
    opens_at    TIME     NOT NULL,
    closes_at   TIME     NOT NULL,
    CONSTRAINT pk_store_opening_hours PRIMARY KEY (store_id, day_of_week),
    CONSTRAINT ck_store_opening_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_store_opening_hours_period CHECK (opens_at <> closes_at)
);
