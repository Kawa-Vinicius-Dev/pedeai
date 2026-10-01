package com.pedeai.catalog.dto;

import java.util.List;

/**
 * Resultado da importação. Tudo ou nada: com qualquer erro nada é gravado ({@code applied = false}). Na
 * pré-visualização, os números dizem o que seria feito, e também nada é gravado.
 */
public record CatalogImportResponse(
        boolean applied,
        int categoriesCreated,
        int productsCreated,
        int productsUpdated,
        int optionGroupsCreated,
        List<ImportIssueResponse> errors
) {
}
