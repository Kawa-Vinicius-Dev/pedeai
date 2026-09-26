package com.pedeai.printing.domain;

/** Documentos que a loja imprime (ver docs/04-impressao.md#documentos). */
public enum DocumentType {
    /** Só os itens de um setor, sem preço: vai para a cozinha ou o bar. */
    PRODUCTION_TICKET,
    /** O pedido inteiro, com valores e pagamento: caixa, expedição e entregador. */
    ORDER_TICKET
}
