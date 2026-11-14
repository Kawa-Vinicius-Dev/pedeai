-- Fidelidade simples: a cada N pedidos concluídos do cliente, o próximo ganha um desconto fixo.
-- loyalty_orders nulo: a loja não tem programa. O prêmio usado fica marcado no próprio pedido.
ALTER TABLE store ADD COLUMN loyalty_orders INTEGER;
ALTER TABLE store ADD COLUMN loyalty_discount_cents BIGINT NOT NULL DEFAULT 0;
ALTER TABLE store ADD CONSTRAINT ck_store_loyalty
    CHECK (loyalty_orders IS NULL OR (loyalty_orders BETWEEN 2 AND 100 AND loyalty_discount_cents > 0));
ALTER TABLE orders ADD COLUMN loyalty_reward BOOLEAN NOT NULL DEFAULT FALSE;
