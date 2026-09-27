package com.pedeai.agent;

import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

/** Entrega bytes à impressora: pela rede (socket TCP, porta 9100) ou pelo spooler do Windows (envio RAW). */
final class Printers {
    static final int DEFAULT_PORT = 9100;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int WRITE_TIMEOUT_MS = 10_000;

    private Printers() {
    }

    static void sendToNetwork(String host, int port, byte[] data) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(WRITE_TIMEOUT_MS);
            OutputStream output = socket.getOutputStream();
            output.write(data);
            output.flush();
        }
    }

    /**
     * Pelo nome instalado no Windows. AUTOSENSE manda os bytes como estão (RAW), sem o driver desenhar nada: serve
     * driver do fabricante ou "Generic / Text Only".
     */
    static void sendToSystemPrinter(String name, byte[] data) throws PrintException {
        PrintService service = Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .filter(candidate -> candidate.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new PrintException("Impressora \"" + name + "\" não encontrada. Instaladas: "
                        + installed()));
        DocPrintJob job = service.createPrintJob();
        job.print(new SimpleDoc(data, DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null);
    }

    static List<String> installed() {
        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null)).map(PrintService::getName).toList();
    }
}
