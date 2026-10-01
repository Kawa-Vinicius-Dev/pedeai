-- Disputas (docs/05-integracoes.md#disputas): o cliente pede cancelamento pelo app e a loja aceita ou recusa no prazo.

CREATE TABLE marketplace_dispute (
    id                  UUID         PRIMARY KEY,
    store_id            UUID         NOT NULL REFERENCES store (id),
    order_id            UUID         NOT NULL REFERENCES orders (id),
    provider            VARCHAR(20)  NOT NULL,
    external_dispute_id VARCHAR(80)  NOT NULL,
    kind                VARCHAR(40)  NOT NULL,
    message             VARCHAR(500),
    expires_at          TIMESTAMP WITH TIME ZONE,
    status              VARCHAR(10)  NOT NULL,
    decided_by          UUID,
    decided_at          TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    version             BIGINT       NOT NULL,
    CONSTRAINT uk_marketplace_dispute UNIQUE (provider, external_dispute_id),
    CONSTRAINT ck_marketplace_dispute_status CHECK (status IN ('OPEN', 'ACCEPTED', 'REJECTED', 'CLOSED'))
);

CREATE INDEX ix_marketplace_dispute_store_status ON marketplace_dispute (store_id, status);

ALTER TABLE outbound_action DROP CONSTRAINT ck_outbound_action_action;
ALTER TABLE outbound_action ADD CONSTRAINT ck_outbound_action_action
    CHECK (action IN ('CONFIRM', 'START_PREPARATION', 'READY', 'DISPATCH', 'REQUEST_CANCELLATION', 'ACCEPT_DISPUTE',
                      'REJECT_DISPUTE'));
