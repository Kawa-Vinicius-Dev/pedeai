package com.pedeai.shared.storage;

import com.pedeai.shared.exception.BusinessRuleException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fotos dos produtos no Cloudflare R2 (API do S3, assinatura SigV4 feita aqui para não trazer o SDK da AWS inteiro).
 * Sem as variáveis R2_*, as fotos ficam desligadas e a tela avisa.
 */
@Component
@EnableConfigurationProperties(ImageStorage.R2Properties.class)
public class ImageStorage {
    public static final String DISABLED = "As fotos estão desligadas neste servidor: falta configurar o Cloudflare R2.";
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final String EMPTY_HASH = sha256Hex(new byte[0]);

    @ConfigurationProperties("app.r2")
    public record R2Properties(String accountId, String accessKeyId, String secretAccessKey, String bucket,
                               String publicUrl) {
        public boolean configured() {
            return filled(accountId) && filled(accessKeyId) && filled(secretAccessKey) && filled(bucket)
                    && filled(publicUrl);
        }

        private static boolean filled(String value) {
            return value != null && !value.isBlank();
        }
    }

    public record Image(byte[] bytes, String contentType) {
    }

    private final R2Properties r2;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ImageStorage(R2Properties r2, Clock clock) {
        this.r2 = r2;
        this.clock = clock;
    }

    public boolean enabled() {
        return r2.configured();
    }

    /** Guarda a foto e devolve o endereço público dela. */
    public String put(String key, byte[] bytes, String contentType) {
        if (!enabled()) {
            throw new BusinessRuleException(DISABLED);
        }
        HttpResponse<byte[]> response = send("PUT", key, bytes, Map.of("content-type", contentType));
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("R2 respondeu " + response.statusCode() + " ao guardar a foto.");
        }
        return publicBase() + key;
    }

    /** Apaga a foto antiga. Só endereços deste bucket; falhar aqui só deixa um arquivo órfão. */
    public void delete(String url) {
        if (enabled() && url != null && url.startsWith(publicBase())) {
            send("DELETE", url.substring(publicBase().length()), new byte[0], Map.of());
        }
    }

    /** Lê uma foto guardada aqui (para mandar ao iFood). Endereço de fora não é baixado. */
    public Image download(String url) {
        if (!enabled() || url == null || !url.startsWith(publicBase())) {
            throw new IllegalArgumentException("Foto fora do armazenamento do PedeAí: " + url);
        }
        try {
            HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("R2 respondeu " + response.statusCode() + " ao ler a foto.");
            }
            return new Image(response.body(), response.headers().firstValue("content-type").orElse("image/jpeg"));
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler a foto: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private String publicBase() {
        return r2.publicUrl().endsWith("/") ? r2.publicUrl() : r2.publicUrl() + "/";
    }

    private HttpResponse<byte[]> send(String method, String key, byte[] body, Map<String, String> headers) {
        String host = r2.accountId() + ".r2.cloudflarestorage.com";
        String path = "/" + r2.bucket() + "/" + key;
        String payloadHash = sha256Hex(body);
        Instant now = Instant.now(clock);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("https://" + host + path))
                .timeout(Duration.ofSeconds(30))
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body))
                .header("x-amz-date", AMZ_DATE.format(now))
                .header("x-amz-content-sha256", payloadHash)
                .header("authorization", authorization(method, host, path, headers, payloadHash, now,
                        r2.accessKeyId(), r2.secretAccessKey(), "auto"));
        headers.forEach(request::header);
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível falar com o R2: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Cabeçalho Authorization da AWS Signature Version 4 (serviço s3). Assina o host, os cabeçalhos dados e os
     * x-amz-*; o caminho já vem codificado (as chaves daqui só têm letras, números, "-", "." e "/").
     */
    static String authorization(String method, String host, String path, Map<String, String> headers,
                                String payloadHash, Instant now, String accessKey, String secret, String region) {
        String amzDate = AMZ_DATE.format(now);
        String day = amzDate.substring(0, 8);
        TreeMap<String, String> signed = new TreeMap<>();
        headers.forEach((name, value) -> signed.put(name.toLowerCase(), value.trim()));
        signed.put("host", host);
        signed.put("x-amz-content-sha256", payloadHash);
        signed.put("x-amz-date", amzDate);
        StringBuilder canonicalHeaders = new StringBuilder();
        signed.forEach((name, value) -> canonicalHeaders.append(name).append(':').append(value).append('\n'));
        String signedHeaders = String.join(";", signed.keySet());
        String canonicalRequest = method + "\n" + path + "\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n"
                + payloadHash;
        String scope = day + "/" + region + "/s3/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] key = hmac(("AWS4" + secret).getBytes(StandardCharsets.UTF_8), day);
        key = hmac(key, region);
        key = hmac(key, "s3");
        key = hmac(key, "aws4_request");
        String signature = HexFormat.of().formatHex(hmac(key, stringToSign));
        return "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String emptyHash() {
        return EMPTY_HASH;
    }
}
