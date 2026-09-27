package com.pedeai.printing.domain;

/** Informado pelo agente no heartbeat. {@code UNKNOWN} até o primeiro aviso. */
public enum PrinterStatus {
    UNKNOWN, ONLINE, OFFLINE, ERROR
}
