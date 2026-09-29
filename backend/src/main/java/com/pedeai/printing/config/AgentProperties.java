package com.pedeai.printing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Versão mais recente do agente de impressão e onde baixar. O agente e a tela avisam quando um computador está
 * desatualizado (docs/04-impressao.md#o-agente); a atualização automática fica para depois.
 */
@ConfigurationProperties("app.agent")
public record AgentProperties(@DefaultValue("0.2.0") String latestVersion, String downloadUrl) {

    /** "0.2.0" é mais nova que "0.1.9". Versão que não é número (ou ausente) conta como desatualizada. */
    public boolean isOutdated(String version) {
        if (version == null || version.isBlank()) {
            return true;
        }
        String[] current = version.trim().split("[.]");
        String[] latest = latestVersion.trim().split("[.]");
        try {
            for (int i = 0; i < Math.max(current.length, latest.length); i++) {
                int have = i < current.length ? Integer.parseInt(current[i]) : 0;
                int want = i < latest.length ? Integer.parseInt(latest[i]) : 0;
                if (have != want) {
                    return have < want;
                }
            }
            return false;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}
