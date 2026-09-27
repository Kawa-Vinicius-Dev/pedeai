-- "Pedido 42 · Cozinha": o painel de impressões mostra sem carregar cada pedido.
ALTER TABLE print_job ADD COLUMN title VARCHAR(120) NOT NULL DEFAULT '';
