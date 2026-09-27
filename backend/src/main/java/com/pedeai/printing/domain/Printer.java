package com.pedeai.printing.domain;

import com.pedeai.shared.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Impressora térmica ESC/POS. Tabela de caracteres, colunas e corte vêm da página de teste do agente. */
@Entity
@Table(name = "printer")
public class Printer {
    @Id
    private UUID id;
    private UUID storeId;
    private UUID agentId;
    private String name;
    @Enumerated(EnumType.STRING)
    private ConnectionType connectionType;
    private String host;
    private Integer port;
    private String systemName;
    private int paperWidthMm;
    private int columns;
    @Enumerated(EnumType.STRING)
    private Codepage codepage;
    @Enumerated(EnumType.STRING)
    private CutMode cutMode;
    private boolean active;
    @Enumerated(EnumType.STRING)
    private PrinterStatus status;
    private String statusDetail;
    private Instant statusUpdatedAt;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected Printer() {
    }

    public Printer(UUID storeId, Instant now) {
        this.id = UuidV7.generate();
        this.storeId = storeId;
        this.status = PrinterStatus.UNKNOWN;
        this.createdAt = now;
    }

    /** Rede usa host e porta; sistema usa o nome no Windows. O outro par fica nulo. */
    public void configure(UUID agentId, String name, ConnectionType connectionType, String host, Integer port,
                          String systemName, int paperWidthMm, int columns, Codepage codepage, CutMode cutMode,
                          boolean active, Instant now) {
        boolean network = connectionType == ConnectionType.NETWORK;
        this.agentId = agentId;
        this.name = name;
        this.connectionType = connectionType;
        this.host = network ? host : null;
        this.port = network ? port : null;
        this.systemName = network ? null : systemName;
        this.paperWidthMm = paperWidthMm;
        this.columns = columns;
        this.codepage = codepage;
        this.cutMode = cutMode;
        this.active = active;
        this.updatedAt = now;
    }

    public void reportStatus(PrinterStatus status, String detail, Instant now) {
        this.status = status;
        this.statusDetail = detail;
        this.statusUpdatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public UUID getAgentId() {
        return agentId;
    }

    public String getName() {
        return name;
    }

    public ConnectionType getConnectionType() {
        return connectionType;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getSystemName() {
        return systemName;
    }

    public int getPaperWidthMm() {
        return paperWidthMm;
    }

    public int getColumns() {
        return columns;
    }

    public Codepage getCodepage() {
        return codepage;
    }

    public CutMode getCutMode() {
        return cutMode;
    }

    public boolean isActive() {
        return active;
    }

    public PrinterStatus getStatus() {
        return status;
    }

    public String getStatusDetail() {
        return statusDetail;
    }

    public Instant getStatusUpdatedAt() {
        return statusUpdatedAt;
    }
}
