package com.pedeai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupTest {

    @Test
    void scriptStartsTheAgentMinimizedInItsOwnWindow() {
        assertEquals("@echo off\r\nstart \"PedeAi Agente\" /min \"C:\\PedeAi\\PedeAiAgente.exe\" rodar\r\n",
                Startup.script("\"C:\\PedeAi\\PedeAiAgente.exe\""));
    }

    @Test
    void fromTheJarTheCommandIsJavaWithTheJar() {
        // Nos testes o processo é o java.exe: o comando precisa levar o -jar (ou o diretório de classes).
        String command = Startup.launchCommand();
        assertTrue(command.contains("java"), command);
        assertTrue(command.contains(" -jar \""), command);
    }
}
