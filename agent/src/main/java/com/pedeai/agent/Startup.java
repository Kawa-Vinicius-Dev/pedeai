package com.pedeai.agent;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Faz o agente abrir sozinho quando a pessoa entra no Windows: um atalho (.cmd) na pasta Inicializar do usuário.
 * Não precisa de administrador. Rodar sem ninguém logado (serviço do Windows) fica para depois.
 */
final class Startup {
    static final String FILE = "PedeAi Agente.cmd";

    private Startup() {
    }

    /** %APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup, ou vazio fora do Windows. */
    static Optional<Path> folder() {
        String appData = System.getenv("APPDATA");
        return appData == null ? Optional.empty()
                : Optional.of(Path.of(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup"));
    }

    /** Grava o atalho com o comando que abriu este agente (o .exe do instalador, ou java -jar). */
    static Optional<Path> enable() throws IOException {
        Optional<Path> folder = folder();
        if (folder.isEmpty()) {
            return Optional.empty();
        }
        Files.createDirectories(folder.get());
        Path file = folder.get().resolve(FILE);
        Files.writeString(file, script(launchCommand()), StandardCharsets.UTF_8);
        return Optional.of(file);
    }

    static boolean disable() throws IOException {
        return folder().isPresent() && Files.deleteIfExists(folder().get().resolve(FILE));
    }

    /** Abre minimizado, numa janela própria, e segue imprimindo. */
    static String script(String command) {
        return "@echo off\r\nstart \"PedeAi Agente\" /min " + command + " rodar\r\n";
    }

    /**
     * Pelo instalador, o processo é o próprio PedeAiAgente.exe. Pelo jar, é o java.exe, e o jar vem da origem da
     * classe.
     */
    static String launchCommand() {
        String executable = ProcessHandle.current().info().command().orElse("java");
        if (!Path.of(executable).getFileName().toString().toLowerCase().startsWith("java")) {
            return quote(executable);
        }
        try {
            Path jar = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return quote(executable) + " -jar " + quote(jar.toString());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Não achei o jar do agente.", e);
        }
    }

    private static String quote(String path) {
        return "\"" + path + "\"";
    }
}
