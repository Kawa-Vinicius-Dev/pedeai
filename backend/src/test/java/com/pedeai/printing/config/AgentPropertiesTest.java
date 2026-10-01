package com.pedeai.printing.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPropertiesTest {
    private final AgentProperties properties = new AgentProperties("0.2.10", null);

    @Test
    void comparesVersionsByNumberNotByText() {
        assertThat(properties.isOutdated("0.2.9")).isTrue();
        assertThat(properties.isOutdated("0.2.10")).isFalse();
        assertThat(properties.isOutdated("0.3")).isFalse();
        assertThat(properties.isOutdated("1.0.0")).isFalse();
    }

    @Test
    void unknownVersionCountsAsOutdated() {
        assertThat(properties.isOutdated(null)).isTrue();
        assertThat(properties.isOutdated("dev")).isTrue();
    }
}
