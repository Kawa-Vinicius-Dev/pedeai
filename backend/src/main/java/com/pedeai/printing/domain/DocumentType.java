package com.pedeai.printing.domain;

/** Documentos que a loja imprime (ver docs/04-impressao.md#documentos). */
public enum DocumentType {
    /** Só os itens de um setor, sem preço: vai para a cozinha ou o bar. */
    PRODUCTION_TICKET,
    /** O pedido inteiro, com valores e pagamento: caixa, expedição e entregador. */
    ORDER_TICKET,
    /** "CANCELADO - NÃO PREPARAR": só para os setores que já imprimiram o pedido. */
    CANCELLATION_TICKET,
    /** Fechamento do caixa: esperado, contado e diferença por forma de pagamento. Não é de pedido. */
    CASH_REPORT
}
