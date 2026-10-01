-- Open Delivery (docs/05-integracoes.md#open-delivery): a 99Food usa o padrão, e outro app compatível entra como
-- OPEN_DELIVERY.

ALTER TABLE orders DROP CONSTRAINT ck_orders_source;
ALTER TABLE orders ADD CONSTRAINT ck_orders_source
    CHECK (source IN ('PEDEAI', 'DIGITAL_MENU', 'API', 'IFOOD', 'NINETY_NINE_FOOD', 'OPEN_DELIVERY'));

ALTER TABLE marketplace_connection DROP CONSTRAINT ck_marketplace_connection_provider;
ALTER TABLE marketplace_connection ADD CONSTRAINT ck_marketplace_connection_provider
    CHECK (provider IN ('IFOOD', 'NINETY_NINE_FOOD', 'OPEN_DELIVERY'));
