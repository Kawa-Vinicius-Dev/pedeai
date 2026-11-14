-- Estoque simples: quantos ainda dá para vender. Nulo: o produto não controla estoque.
-- Cada pedido desconta; ao chegar a zero o produto é pausado sozinho (docs/08-mercado.md).
ALTER TABLE product ADD COLUMN stock_quantity INTEGER;
ALTER TABLE product ADD CONSTRAINT ck_product_stock CHECK (stock_quantity IS NULL OR stock_quantity >= 0);
