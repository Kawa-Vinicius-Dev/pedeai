package com.pedeai.shared.id;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7Test {

    @Test
    void generatesVersion7WithRfcVariant() {
        UUID id = UuidV7.generate();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void storesTheTimestampInTheFirst48Bits() {
        long millis = 1_790_000_000_000L;

        UUID id = UuidV7.fromMillis(millis);

        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(millis);
    }

    @Test
    void laterIdsSortAfterEarlierOnes() {
        UUID earlier = UuidV7.fromMillis(1_790_000_000_000L);
        UUID later = UuidV7.fromMillis(1_790_000_000_001L);

        assertThat(later.toString()).isGreaterThan(earlier.toString());
    }
}
