package com.pedeai.catalog.service;

import com.pedeai.catalog.dto.ImportDraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpreadsheetParserTest {

    @Test
    void readsTheBrazilianExcelFormatWithQuotesAndAccents() {
        String csv = """
                ﻿Categoria;Produto;Preço (R$);Descrição;Código;Disponível;Adicionais
                Lanches;X-Burger;R$ 32,90;"Pão, carne; queijo";100;sim;Extras|Molhos
                Lanches;X-Salada;1.234,56;;;não;
                Bebidas;Refrigerante;7;;900;;
                """;

        SpreadsheetParser.Result result = SpreadsheetParser.parse(csv);

        assertThat(result.errors()).isEmpty();
        List<ImportDraft.Category> categories = result.draft().categories();
        assertThat(categories).extracting(ImportDraft.Category::name).containsExactly("Lanches", "Bebidas");
        ImportDraft.Product burger = categories.getFirst().products().getFirst();
        assertThat(burger.priceCents()).isEqualTo(3290);
        assertThat(burger.description()).isEqualTo("Pão, carne; queijo");
        assertThat(burger.code()).isEqualTo("100");
        assertThat(burger.groupNames()).containsExactly("Extras", "Molhos");
        ImportDraft.Product salad = categories.getFirst().products().get(1);
        assertThat(salad.priceCents()).isEqualTo(123456);
        assertThat(salad.available()).isFalse();
        assertThat(salad.code()).isNull();
    }

    @Test
    void commaSeparatedWithDotDecimalsAlsoWorks() {
        SpreadsheetParser.Result result = SpreadsheetParser.parse("nome,categoria,valor\nSuco,Bebidas,9.50\n");

        assertThat(result.errors()).isEmpty();
        assertThat(result.draft().categories().getFirst().products().getFirst().priceCents()).isEqualTo(950);
    }

    @Test
    void pointsToTheLineWithTheProblem() {
        SpreadsheetParser.Result missingColumn = SpreadsheetParser.parse("categoria;produto\nLanches;X\n");
        assertThat(missingColumn.errors()).extracting(issue -> issue.origin() + ": " + issue.message())
                .containsExactly("linha 1: Falta a coluna \"preco\" no cabeçalho. Baixe o modelo para ver as colunas.");

        SpreadsheetParser.Result badRows = SpreadsheetParser.parse("""
                categoria;produto;preco
                Lanches;X-Burger;trinta
                ;Sem categoria;10

                Lanches;Ok;10
                """);
        assertThat(badRows.errors()).extracting(issue -> issue.origin())
                .containsExactly("linha 2", "linha 3");
        assertThat(SpreadsheetParser.cents("-5")).isNull();
    }
}
