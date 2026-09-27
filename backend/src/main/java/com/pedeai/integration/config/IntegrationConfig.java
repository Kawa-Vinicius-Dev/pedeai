package com.pedeai.integration.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IfoodProperties.class)
public class IntegrationConfig {
}
