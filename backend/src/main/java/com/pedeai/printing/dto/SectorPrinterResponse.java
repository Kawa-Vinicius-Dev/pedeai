package com.pedeai.printing.dto;

import com.pedeai.printing.domain.SectorPrinter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record SectorPrinterResponse(
        UUID sectorId,
        UUID printerId,
        @Schema(types = {"string", "null"}) UUID backupPrinterId,
        int copies,
        boolean enabled
) {
    public static SectorPrinterResponse from(SectorPrinter sectorPrinter) {
        return new SectorPrinterResponse(sectorPrinter.getSectorId(), sectorPrinter.getPrinterId(),
                sectorPrinter.getBackupPrinterId(), sectorPrinter.getCopies(), sectorPrinter.isEnabled());
    }
}
