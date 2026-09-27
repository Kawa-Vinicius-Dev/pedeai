package com.pedeai.agent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Impressora de rede falsa: um socket local que guarda o que recebeu. */
class PrintersTest {

    @Test
    void deliversEveryByteToANetworkPrinter() throws Exception {
        byte[] page = Main.testPage(48);
        try (ServerSocket printer = new ServerSocket(0)) {
            CompletableFuture<byte[]> received = CompletableFuture.supplyAsync(() -> {
                try (Socket connection = printer.accept(); InputStream input = connection.getInputStream()) {
                    return input.readAllBytes();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });

            Printers.sendToNetwork("127.0.0.1", printer.getLocalPort(), page);

            assertArrayEquals(page, received.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void failsWhenThePrinterIsOff() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        assertThrows(IOException.class, () -> Printers.sendToNetwork("127.0.0.1", closedPort, new byte[]{1}));
    }
}
