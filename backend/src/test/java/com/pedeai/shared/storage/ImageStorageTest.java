package com.pedeai.shared.storage;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImageStorageTest {

    /** Exemplo "GET Object" da documentação da AWS (Signature Version 4, autenticação por cabeçalho). */
    @Test
    void signsLikeTheAwsExample() {
        String authorization = ImageStorage.authorization("GET", "examplebucket.s3.amazonaws.com", "/test.txt",
                Map.of("Range", "bytes=0-9"), ImageStorage.emptyHash(), Instant.parse("2013-05-24T00:00:00Z"),
                "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "us-east-1");

        assertThat(authorization).isEqualTo("AWS4-HMAC-SHA256 "
                + "Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, "
                + "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date, "
                + "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41");
    }
}
