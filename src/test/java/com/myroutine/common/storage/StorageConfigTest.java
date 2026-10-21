package com.myroutine.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class StorageConfigTest {

    private static final URI ENDPOINT = URI.create("http://minio.internal:9000");
    private static final String KEY = "products/p1/a.jpg";

    @Test
    @DisplayName("서명 주소가 있으면 그 주소로 서명한다.")
    void presignWithPresignEndpoint() {
        // Given
        String presignEndpoint = "https://files.example.test";

        // When
        URL url = presignedUrl(presignEndpoint);

        // Then
        assertThat(url.getProtocol()).isEqualTo("https");
        assertThat(url.getHost()).isEqualTo("files.example.test");
        assertThat(url.getPath()).startsWith("/myroutine/" + KEY);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("서명 주소가 비어 있으면 endpoint로 서명한다.")
    void presignWithEndpointWhenPresignEndpointBlank(String presignEndpoint) {
        // Given

        // When
        URL url = presignedUrl(presignEndpoint);

        // Then
        assertThat(url.getHost()).isEqualTo("minio.internal");
        assertThat(url.getPort()).isEqualTo(9000);
    }

    private URL presignedUrl(String presignEndpoint) {
        StorageProperties properties = new StorageProperties(
                ENDPOINT, presignEndpoint, "us-east-1", "access", "secret",
                "myroutine", "http://minio.internal:9000/myroutine", Duration.ofMinutes(10), 5_242_880L
        );
        try (S3Presigner presigner = new StorageConfig().s3Presigner(properties)) {
            return presigner.presignPutObject(PutObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(10))
                            .putObjectRequest(PutObjectRequest.builder().bucket("myroutine").key(KEY).build())
                            .build())
                    .url();
        }
    }
}
