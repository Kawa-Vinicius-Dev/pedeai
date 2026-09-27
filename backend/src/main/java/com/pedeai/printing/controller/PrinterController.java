package com.pedeai.printing.controller;

import com.pedeai.printing.dto.PrinterRequest;
import com.pedeai.printing.dto.PrinterResponse;
import com.pedeai.printing.dto.SectorPrinterRequest;
import com.pedeai.printing.dto.SectorPrinterResponse;
import com.pedeai.printing.service.PrinterService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize(Permissions.MANAGE_PRINTING)
public class PrinterController {
    private final PrinterService printerService;

    public PrinterController(PrinterService printerService) {
        this.printerService = printerService;
    }

    /** Quem imprime (caixa, cozinha) também escolhe a impressora ao reimprimir; só o cadastro é do gerente. */
    @GetMapping("/api/printers")
    @PreAuthorize(Permissions.ADVANCE_ORDERS)
    public List<PrinterResponse> list(CurrentUser user) {
        return printerService.list(user.storeId());
    }

    @PostMapping("/api/printers")
    public ResponseEntity<PrinterResponse> create(CurrentUser user, @Valid @RequestBody PrinterRequest request) {
        PrinterResponse created = printerService.create(user.storeId(), request);
        return ResponseEntity.created(URI.create("/api/printers/" + created.id())).body(created);
    }

    @PutMapping("/api/printers/{id}")
    public PrinterResponse update(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody PrinterRequest request) {
        return printerService.update(user.storeId(), id, request);
    }

    /** Setores que já têm impressora. Setor fora da lista ainda não imprime. */
    @GetMapping("/api/sector-printers")
    public List<SectorPrinterResponse> sectorPrinters(CurrentUser user) {
        return printerService.listSectorPrinters(user.storeId());
    }

    @PutMapping("/api/sectors/{sectorId}/printer")
    public SectorPrinterResponse assign(CurrentUser user, @PathVariable UUID sectorId,
                                        @Valid @RequestBody SectorPrinterRequest request) {
        return printerService.assignToSector(user.storeId(), sectorId, request);
    }
}
