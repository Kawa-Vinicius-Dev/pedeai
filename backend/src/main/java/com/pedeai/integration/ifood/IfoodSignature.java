package com.pedeai.integration.ifood;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Assinatura do webhook do iFood (docs/05-integracoes.md): HMAC-SHA256 do corpo cru com o client secret, em
 * hexadecimal, no header {@code X-IFood-Signature}. Comparação em tempo constante.
 */
public final class IfoodSignature {
    private IfoodSignature() {
    }

    public static boolean matches(byte[] body, String header, String secret) {
        if (header == null || header.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }
        byte[] expected = sign(body, secret).getBytes(StandardCharsets.US_ASCII);
        byte[] received = header.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, received);
    }

    static String sign(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível na JVM.", e);
        }
    }
}
