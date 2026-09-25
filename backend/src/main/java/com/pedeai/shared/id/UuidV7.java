package com.pedeai.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID versão 7 (RFC 9562): os 48 bits iniciais são o instante em milissegundos,
 * então IDs novos ficam em ordem no índice, e o resto é aleatório.
 */
public final class UuidV7 {
    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        return fromMillis(System.currentTimeMillis());
    }

    static UUID fromMillis(long unixMillis) {
        long randA = RANDOM.nextInt(1 << 12);
        long mostSignificant = (unixMillis << 16) | (0x7L << 12) | randA;
        long leastSignificant = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
