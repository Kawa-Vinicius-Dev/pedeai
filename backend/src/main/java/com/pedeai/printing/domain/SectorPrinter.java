package com.pedeai.printing.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

/** Para qual impressora vai a produção de um setor. A reserva recebe quando a principal está offline. */
@Entity
@Table(name = "sector_printer")
public class SectorPrinter {
    @Id
    private UUID sectorId;
    private UUID storeId;
    private UUID printerId;
    private UUID backupPrinterId;
    private int copies;
    private boolean enabled;
    @Version
    private Long version;

    protected SectorPrinter() {
    }

    public SectorPrinter(UUID sectorId, UUID storeId) {
        this.sectorId = sectorId;
        this.storeId = storeId;
    }

    public void configure(UUID printerId, UUID backupPrinterId, int copies, boolean enabled) {
        this.printerId = printerId;
        this.backupPrinterId = backupPrinterId;
        this.copies = copies;
        this.enabled = enabled;
    }

    public UUID getSectorId() {
        return sectorId;
    }

    public UUID getPrinterId() {
        return printerId;
    }

    public UUID getBackupPrinterId() {
        return backupPrinterId;
    }

    public int getCopies() {
        return copies;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
