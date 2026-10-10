package com.myroutine.product.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.storage.ObjectStorage;
import com.myroutine.common.storage.StorageProperties;
import com.myroutine.product.application.ImageResult;
import com.myroutine.product.application.ProductImageService;
import com.myroutine.product.application.UploadUrlResult;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProductImageControllerTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;
    private final StorageProperties properties;
    private final ProductImageService productImageService;
    private final ObjectStorage objectStorage;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ProductImageControllerTest(
            MockMvc mockMvc,
            TestFixtures testFixtures,
            ObjectMapper objectMapper,
            StorageProperties properties,
            ProductImageService productImageService,
            ObjectStorage objectStorage, JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.productImageService = productImageService;
        this.objectStorage = objectStorage;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("업로드 URL을 발급받아 직접 PUT하고 등록하면, 응답의 url로 인증 없이 같은 파일을 받을 수 있다.")
    void issueUploadUrlAndRegisterImage() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId);
        UploadUrlRequest request = new UploadUrlRequest("image/png", 2000L);

        byte[] fileBytes = new byte[2000];
        new Random().nextBytes(fileBytes);
        HttpClient httpClient = HttpClient.newHttpClient();

        // When & Then
        MvcResult issued = mockMvc.perform(post("/api/shops/{shopId}/products/{productId}/images/presigned-url", shopId, productId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String issuedBody = issued.getResponse().getContentAsString();
        String uploadUrl = JsonPath.read(issuedBody, "$.uploadUrl");
        String objectKey = JsonPath.read(issuedBody, "$.objectKey");
        Map<String, String> signedHeaders = JsonPath.read(issuedBody, "$.headers");

        HttpResponse<String> response = testFixtures.putToStorage(uploadUrl, signedHeaders, fileBytes);
        assertThat(response.statusCode()).isEqualTo(200);

        MvcResult result = mockMvc.perform(post("/api/shops/{shopId}/products/{productId}/images", shopId, productId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterImageRequest(objectKey))))
                .andExpect(status().isCreated())
                .andReturn();
        String url = JsonPath.read(result.getResponse().getContentAsString(), "$.url");

        HttpResponse<byte[]> getResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(getResponse.body()).isEqualTo(fileBytes);
    }

    @Test
    @DisplayName("이미지를 등록하면 상품 상세에 images가 포함된다.")
    void getProductDetailWithImages() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId);
        ImageResult imageResult = testFixtures.registerProductImage(memberId, shopId, productId);

        String expectedUrl = properties.publicBaseUrl() + "/" + imageResult.objectKey();

        // When & Then
        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images", hasSize(1)))
                .andExpect(jsonPath("$.images[0].imageId").value(imageResult.imageId().toString()))
                .andExpect(jsonPath("$.images[0].url").value(expectedUrl))
                .andExpect(jsonPath("$.thumbnailUrl").value(expectedUrl));
    }

    @Test
    @DisplayName("이미지를 등록하면 상품 목록에 thumbnailUrl이 포함된다.")
    void getProductsWithThumbnailUrl() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId);
        ImageResult imageResult = testFixtures.registerProductImage(memberId, shopId, productId);

        String expectedUrl = properties.publicBaseUrl() + "/" + imageResult.objectKey();

        // When & Then
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value(expectedUrl));
    }

    @Test
    @DisplayName("이미지 업로드를 실패한다. (서명과 다른 Content-Type, 저장소가 403으로 거절)")
    void putWithDifferentContentType() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId);
        byte[] fileBytes = new byte[2000];
        UploadUrlResult issued = productImageService.issueUploadUrl(memberId, shopId, productId, "image/png", fileBytes.length);
        Map<String, String> wrongHeaders = new HashMap<>(issued.headers());
        wrongHeaders.put("content-type", "image/gif");

        // When
        HttpResponse<String> response = testFixtures.putToStorage(issued.uploadUrl(), wrongHeaders, fileBytes);

        // Then
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("SignatureDoesNotMatch");
        assertThat(objectStorage.exists(issued.objectKey())).isFalse();
    }

    @Test
    @DisplayName("이미지 등록을 실패한다. (업로드하지 않은 객체)")
    void registerImageNotUploaded() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId);
        byte[] fileBytes = new byte[2000];
        UploadUrlResult issued = productImageService.issueUploadUrl(memberId, shopId, productId, "image/png", fileBytes.length);

        // When & Then
        mockMvc.perform(post("/api/shops/{shopId}/products/{productId}/images", shopId, productId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterImageRequest(issued.objectKey()))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_UPLOADED"));
    }

    @Test
    @DisplayName("이미지 등록을 실패한다. (다른 상품의 접두어를 가진 키)")
    void registerImageWithOtherProductKey() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productAId = testFixtures.registerProduct(memberId, shopId);
        UUID productBId = testFixtures.registerProduct(memberId, shopId);
        ImageResult imageB = testFixtures.registerProductImage(memberId, shopId, productBId);

        // When & Then
        mockMvc.perform(post("/api/shops/{shopId}/products/{productId}/images", shopId, productAId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterImageRequest(imageB.objectKey()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("업로드 URL 발급을 실패한다. (허용되지 않은 형식)")
    void issueUploadUrlWithUnsupportedContentType() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId);
        UploadUrlRequest request = new UploadUrlRequest(
                "image/gif",
                10000L
        );

        // When & Then
        mockMvc.perform(post("/api/shops/{shopId}/products/{productId}/images/presigned-url", shopId, productId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("업로드 URL 발급을 실패한다. (5MB 초과)")
    void issueUploadUrlExceedingMaxSize() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId);
        UploadUrlRequest request = new UploadUrlRequest(
                "image/jpeg",
                5242881L
        );

        // When & Then
        mockMvc.perform(post("/api/shops/{shopId}/products/{productId}/images/presigned-url", shopId, productId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("이미지 관련 요청을 실패한다. (다른 회원의 가게)")
    void imageRequestOfOtherMemberShop() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId);
        UploadUrlRequest request = new UploadUrlRequest(
                "image/jpeg",
                10000L
        );

        String token = testFixtures.token("other@test.com");

        // When & Then
        mockMvc.perform(post("/api/shops/{shopId}/products/{productId}/images/presigned-url", shopId, productId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("이미지를 삭제하면 DB와 저장소에서 모두 사라진다.")
    void deleteImage() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId);
        ImageResult imageResult = testFixtures.registerProductImage(memberId, shopId, productId);

        // When & Then
        mockMvc.perform(delete("/api/shops/{shopId}/products/{productId}/images/{imageId}", shopId, productId, imageResult.imageId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(objectStorage.exists(imageResult.objectKey())).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product.product_image WHERE id = ?", Long.class, imageResult.imageId())).isEqualTo(0);
    }
}
