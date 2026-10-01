package com.pedeai.catalog.controller;

import com.pedeai.catalog.dto.CatalogImportResponse;
import com.pedeai.catalog.dto.SpreadsheetImportRequest;
import com.pedeai.catalog.service.CatalogImportService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Importação de cardápio por planilha. Com {@code dryRun}, só mostra o que seria feito. */
@RestController
@PreAuthorize(Permissions.MANAGE_CATALOG)
public class CatalogImportController {
    private final CatalogImportService catalogImportService;

    public CatalogImportController(CatalogImportService catalogImportService) {
        this.catalogImportService = catalogImportService;
    }

    @PostMapping("/api/catalog/imports/spreadsheet")
    public CatalogImportResponse importSpreadsheet(CurrentUser user,
                                                   @Valid @RequestBody SpreadsheetImportRequest request) {
        return catalogImportService.importSpreadsheet(user.storeId(), request.content(), request.dryRun());
    }
}
