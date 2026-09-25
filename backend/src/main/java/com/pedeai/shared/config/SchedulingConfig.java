package com.pedeai.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tarefas periódicas: keepalive do tempo real e, nas próximas etapas, os workers das filas. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
