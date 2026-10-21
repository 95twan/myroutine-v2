package com.myroutine.common.storage;

import com.myroutine.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "myroutine.storage.presign-endpoint=https://files.example.test")
class PresignEndpointIntegrationTest extends IntegrationTestSupport {

    private final ObjectStorage objectStorage;

    @Autowired
    public PresignEndpointIntegrationTest(ObjectStorage objectStorage) {
        this.objectStorage = objectStorage;
    }

    @Test
    @DisplayName("업로드 URL만 공개 주소로 서명하고 서버의 MinIO 호출은 endpoint로 보낸다.")
    void presignOnlyUsesPresignEndpoint() {
        // Given

        // When
        PresignedUpload presigned = objectStorage.presignPut("products/p1/a.jpg", "image/jpeg", 1024);
        boolean exists = objectStorage.exists("products/p1/none.jpg");

        // Then
        assertThat(presigned.url()).startsWith("https://files.example.test/myroutine/products/p1/a.jpg");
        assertThat(exists).isFalse();
    }
}
