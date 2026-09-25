package com.pedeai.catalog.domain;

/** Como as opções escolhidas num grupo somam ao preço do item. */
public enum PricingRule {
    /** Soma o preço de cada opção (vezes a quantidade). Ex.: adicionais de lanche. */
    SUM,
    /** Vale a opção mais cara. Ex.: pizza meio a meio cobrada pelo sabor mais caro. */
    MAX,
    /** Média das opções escolhidas. Ex.: pizza meio a meio cobrada pela média dos sabores. */
    AVERAGE
}
