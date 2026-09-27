package com.pedeai.printing.domain;

/** Rede: o agente abre um socket em host:port (9100). Sistema: envia RAW pelo spooler do Windows, pelo nome. */
public enum ConnectionType {
    NETWORK, SYSTEM
}
