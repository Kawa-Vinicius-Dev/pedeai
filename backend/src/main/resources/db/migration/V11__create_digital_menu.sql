-- Cardápio digital PedeAí (docs/06-roadmap.md#etapa-8--cardápio-digital-pedeaí).

-- Endereço público da loja (pedeai.com.br/loja/<slug>) e a chave "recebendo pedidos pelo cardápio".
ALTER TABLE store ADD COLUMN slug VARCHAR(60);
-- Lojas que já existiam ganham um endereço provisório e único; o dono troca na tela da loja.
UPDATE store SET slug = 'loja-' || substring(cast(id as varchar(36)), 25, 12);
ALTER TABLE store ALTER COLUMN slug SET NOT NULL;
ALTER TABLE store ADD CONSTRAINT uk_store_slug UNIQUE (slug);
ALTER TABLE store ADD COLUMN menu_open BOOLEAN NOT NULL DEFAULT FALSE;

-- Pedido feito pelo cliente no cardápio: origem própria e um código aleatório para ele acompanhar o pedido.
ALTER TABLE orders DROP CONSTRAINT ck_orders_source;
ALTER TABLE orders ADD CONSTRAINT ck_orders_source
    CHECK (source IN ('PEDEAI', 'DIGITAL_MENU', 'IFOOD', 'NINETY_NINE_FOOD'));
ALTER TABLE orders ADD COLUMN tracking_code VARCHAR(40);
ALTER TABLE orders ADD CONSTRAINT uk_orders_tracking_code UNIQUE (tracking_code);

ALTER TABLE order_status_history DROP CONSTRAINT ck_order_status_history_actor;
ALTER TABLE order_status_history ADD CONSTRAINT ck_order_status_history_actor
    CHECK (actor_type IN ('USER', 'SYSTEM', 'MARKETPLACE', 'CUSTOMER'));
