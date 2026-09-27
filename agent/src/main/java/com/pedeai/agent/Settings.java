package com.pedeai.agent;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * O que o pareamento deixa neste computador: a URL da API, o token de dispositivo e o nome. Fica na pasta do usuário
 * (%APPDATA%\PedeAi no Windows), junto com o diário. O token só serve para imprimir e é revogado na tela.
 */
record Settings(String url, String token, String name) {
    private static final String FILE = "agente.properties";

    static Path folder() {
        String appData = System.getenv("APPDATA");
        return appData != null ? Path.of(appData, "PedeAi") : Path.of(System.getProperty("user.home"), ".pedeai");
    }

    static Settings load() throws IOException {
        Path file = folder().resolve(FILE);
        if (!Files.exists(file)) {
            return null;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return new Settings(properties.getProperty("url"), properties.getProperty("token"),
                properties.getProperty("name"));
    }

    static void save(Settings settings) throws IOException {
        Files.createDirectories(folder());
        Properties properties = new Properties();
        properties.setProperty("url", settings.url());
        properties.setProperty("token", settings.token());
        properties.setProperty("name", settings.name());
        try (Writer writer = Files.newBufferedWriter(folder().resolve(FILE), StandardCharsets.UTF_8)) {
            properties.store(writer, "PedeAi agente de impressao");
        }
    }
}
