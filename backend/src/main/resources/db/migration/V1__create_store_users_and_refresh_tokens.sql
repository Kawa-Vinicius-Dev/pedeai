CREATE TABLE store (
    id                           UUID         PRIMARY KEY,
    name                         VARCHAR(120) NOT NULL,
    document                     VARCHAR(20),
    phone                        VARCHAR(20),
    timezone                     VARCHAR(60)  NOT NULL,
    business_day_cutoff          TIME         NOT NULL,
    service_fee_bp               INTEGER      NOT NULL,
    auto_confirm_own_orders      BOOLEAN      NOT NULL,
    start_preparation_on_confirm BOOLEAN      NOT NULL,
    created_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                   TIMESTAMP WITH TIME ZONE NOT NULL,
    version                      BIGINT       NOT NULL,
    CONSTRAINT ck_store_service_fee CHECK (service_fee_bp BETWEEN 0 AND 3000)
);

CREATE TABLE app_user (
    id            UUID         PRIMARY KEY,
    store_id      UUID         NOT NULL REFERENCES store (id),
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    version       BIGINT       NOT NULL,
    CONSTRAINT uk_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_role CHECK (role IN ('OWNER', 'MANAGER', 'CASHIER', 'WAITER', 'KITCHEN'))
);

CREATE INDEX ix_app_user_store ON app_user (store_id);

CREATE TABLE refresh_token (
    id          UUID         PRIMARY KEY,
    store_id    UUID         NOT NULL REFERENCES store (id),
    user_id     UUID         NOT NULL REFERENCES app_user (id),
    token_hash  VARCHAR(64)  NOT NULL,
    device_name VARCHAR(200),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at  TIMESTAMP WITH TIME ZONE,
    revoke_reason VARCHAR(20),
    version     BIGINT       NOT NULL,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_token_reason CHECK (revoke_reason IN ('ROTATED', 'LOGOUT', 'REVOKED'))
);

CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);
