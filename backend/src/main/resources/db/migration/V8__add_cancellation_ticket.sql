-- Aviso de cancelamento para os setores que já imprimiram o pedido (docs/04-impressao.md#documentos).
ALTER TABLE print_job DROP CONSTRAINT ck_print_job_document;
ALTER TABLE print_job ADD CONSTRAINT ck_print_job_document
    CHECK (document_type IN ('PRODUCTION_TICKET', 'ORDER_TICKET', 'CANCELLATION_TICKET'));
