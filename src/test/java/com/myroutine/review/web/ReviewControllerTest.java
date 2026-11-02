package com.myroutine.review.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.model.Ids;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.ReviewStatSnapshot;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ReviewControllerTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    public ReviewControllerTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("리뷰를 작성하면 평점 통계가 쌓인다.")
    void writeAccumulatesStats() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        // When & Then
        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").isNotEmpty());

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").isNotEmpty());

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").isNotEmpty());

        ReviewStatSnapshot rs = testFixtures.reviewStatOf(productAId);
        assertThat(rs.reviewCount()).isEqualTo(3);
        assertThat(rs.ratingSum()).isEqualTo(13);
        assertThat(rs.rating(1)).isEqualTo(0);
        assertThat(rs.rating(2)).isEqualTo(0);
        assertThat(rs.rating(3)).isEqualTo(0);
        assertThat(rs.rating(4)).isEqualTo(2);
        assertThat(rs.rating(5)).isEqualTo(1);

        mockMvc.perform(get("/api/products/{id}/review-stats", productAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(3))
                .andExpect(jsonPath("$.average").value(4.3))
                .andExpect(jsonPath("$.distribution['5']").value(1))
                .andExpect(jsonPath("$.distribution['4']").value(2))
                .andExpect(jsonPath("$.distribution['3']").value(0))
                .andExpect(jsonPath("$.distribution['2']").value(0))
                .andExpect(jsonPath("$.distribution['1']").value(0));

    }

    @ParameterizedTest
    @MethodSource("invalidWriteReviewInputProvider")
    @DisplayName("범위를 벗어난 별점·본문이나 품목 ID 누락으로 리뷰 작성을 실패한다. (INVALID_REQUEST)")
    void writeWithInvalidInput(int rating, String content, boolean withOrderLineId) throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        Map<String, Object> body = new HashMap<>();
        body.put("rating", rating);
        body.put("content", content);
        if (withOrderLineId) {
            body.put("orderLineId", orderLine1Id);
        }

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    public static Stream<Arguments> invalidWriteReviewInputProvider() {
        return Stream.of(
                Arguments.of(0, "효과가 좋아요 정말 만족합니다", true),
                Arguments.of(6, "효과가 좋아요 정말 만족합니다", true),
                Arguments.of(5, "가".repeat(9), true),
                Arguments.of(5, "가".repeat(1001), true),
                Arguments.of(5, "효과가 좋아요 정말 만족합니다", false)
        );
    }

    @Test
    @DisplayName("경계값(별점 1, 본문 10자)으로 리뷰를 작성한다.")
    void writeWithBoundaryInput() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 1, "가".repeat(10));

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("구매확정 전 품목에 리뷰 작성을 실패한다. (REVIEW_NOT_ALLOWED)")
    void writeBeforeConfirmed() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID orderId = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, productAId);

        UUID orderLineId = testFixtures.orderLineIdOf(orderId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLineId, 5, "효과가 좋아요 정말 만족합니다");

        // When & Then
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));

        assertThat(testFixtures.reviewCountOf(productAId)).isEqualTo(0);
        assertThat(testFixtures.reviewStatOf(productAId)).isNull();
    }

    @Test
    @DisplayName("남의 품목에 리뷰 작성을 실패한다. (ORDER_LINE_NOT_FOUND)")
    void writeOtherMembersLine() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        UUID orderLineId = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        String token = testFixtures.token("buyer2@test.com");

        WriteReviewRequest request = new WriteReviewRequest(orderLineId, 5, "효과가 좋아요 정말 만족합니다");

        // When & Then
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_LINE_NOT_FOUND"));

        assertThat(testFixtures.reviewCountOf(productAId)).isEqualTo(0);
        assertThat(testFixtures.reviewStatOf(productAId)).isNull();
    }

    @Test
    @DisplayName("삭제한 품목에 다시 리뷰 작성을 실패한다. (REVIEW_ALREADY_EXISTS)")
    void writeAgainOnDeletedLine() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        mockMvc.perform(delete("/api/reviews/{id}", review1Id)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WriteReviewRequest(orderLine1Id, 4, "효과가 좋아요 정말 만족합니다"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));

        assertThat(testFixtures.reviewCountOf(productAId)).isEqualTo(3);
        assertThat(testFixtures.reviewStatOf(productAId).reviewCount()).isEqualTo(2);
        assertThat(testFixtures.reviewStatOf(productAId).ratingSum()).isEqualTo(8);
        assertThat(testFixtures.reviewStatOf(productAId).rating(4)).isEqualTo(2);
        assertThat(testFixtures.reviewStatOf(productAId).rating(5)).isEqualTo(0);
    }

    @Test
    @DisplayName("별점을 수정하면 통계 분포가 옮겨 간다.")
    void editMovesDistribution() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review3Id = JsonPath.read(result.getResponse().getContentAsString(), "$.reviewId");

        // When & Then
        mockMvc.perform(patch("/api/reviews/{id}", review3Id)
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditReviewRequest(2, "수정한 본문입니다 열 글자 이상"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewId").value(review3Id))
                .andExpect(jsonPath("$.rating").value(2))
                .andExpect(jsonPath("$.content").value("수정한 본문입니다 열 글자 이상"));

        ReviewStatSnapshot rs = testFixtures.reviewStatOf(productAId);
        assertThat(rs.reviewCount()).isEqualTo(3);
        assertThat(rs.ratingSum()).isEqualTo(11);
        assertThat(rs.rating(2)).isEqualTo(1);
        assertThat(rs.rating(4)).isEqualTo(1);
        assertThat(rs.rating(5)).isEqualTo(1);

        mockMvc.perform(get("/api/products/{id}/review-stats", productAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(3))
                .andExpect(jsonPath("$.average").value(3.7))
                .andExpect(jsonPath("$.distribution['2']").value(1));
    }

    @Test
    @DisplayName("남의 리뷰 수정을 실패한다. (REVIEW_NOT_FOUND)")
    void editOtherMembersReview() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review3Id = JsonPath.read(result.getResponse().getContentAsString(), "$.reviewId");

        // When & Then
        mockMvc.perform(patch("/api/reviews/{id}", review3Id)
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditReviewRequest(2, "수정한 본문입니다 열 글자 이상"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));

        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review3Id)).rating()).isEqualTo(4);
    }

    @Test
    @DisplayName("작성 후 30일이 지난 리뷰 수정을 실패한다. (REVIEW_EDIT_EXPIRED)")
    void editAfterThirtyDays() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        testFixtures.setReviewCreatedAt(UUID.fromString(review2Id), Instant.now().minus(31, ChronoUnit.DAYS));

        // When & Then
        mockMvc.perform(patch("/api/reviews/{id}", review2Id)
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditReviewRequest(2, "수정한 본문입니다 열 글자 이상"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_EDIT_EXPIRED"));

        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review3Id)).rating()).isEqualTo(4);
        assertThat(testFixtures.reviewStatOf(productAId).ratingSum()).isEqualTo(13);
    }

    @Test
    @DisplayName("삭제한 리뷰 수정을 실패한다. (REVIEW_NOT_FOUND)")
    void editDeletedReview() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        mockMvc.perform(delete("/api/reviews/{id}", review1Id)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(patch("/api/reviews/{id}", review1Id)
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditReviewRequest(2, "수정한 본문입니다 열 글자 이상"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));

        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review1Id)).rating()).isEqualTo(5);
        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review1Id)).content()).isEqualTo("효과가 좋아요 정말 만족합니다");

        assertThat(testFixtures.reviewStatOf(productAId).ratingSum()).isEqualTo(8);
        assertThat(testFixtures.reviewStatOf(productAId).rating(2)).isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"content\":\"수정한 본문입니다 열 글자 이상\"}", "{\"rating\":2}"})
    @DisplayName("별점이나 본문을 빼고 리뷰 수정을 실패한다. (INVALID_REQUEST)")
    void editWithMissingField(String body) throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        // When & Then
        mockMvc.perform(patch("/api/reviews/{id}", review3Id)
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review3Id)).rating()).isEqualTo(4);
        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review3Id)).content()).isEqualTo("효과가 좋아요 정말 만족합니다");

        assertThat(testFixtures.reviewStatOf(productAId).ratingSum()).isEqualTo(13);
    }

    @Test
    @DisplayName("리뷰를 삭제하면 통계에서 빠진다.")
    void deleteRemovesFromStats() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        // When & Then
        mockMvc.perform(delete("/api/reviews/{id}", review1Id)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNoContent());

        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review1Id)).status()).isEqualTo("DELETED");
        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review2Id)).status()).isEqualTo("ACTIVE");
        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review3Id)).status()).isEqualTo("ACTIVE");

        ReviewStatSnapshot rs = testFixtures.reviewStatOf(productAId);
        assertThat(rs.reviewCount()).isEqualTo(2);
        assertThat(rs.ratingSum()).isEqualTo(8);
        assertThat(rs.rating(4)).isEqualTo(2);
        assertThat(rs.rating(5)).isEqualTo(0);

        mockMvc.perform(get("/api/products/{id}/review-stats", productAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value(4.0));
    }

    @Test
    @DisplayName("남의 리뷰 삭제를 실패한다. (REVIEW_NOT_FOUND)")
    void deleteOtherMembersReview() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        // When & Then
        mockMvc.perform(delete("/api/reviews/{id}", review1Id)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));

        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review1Id)).status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("삭제한 리뷰를 다시 삭제하면 실패하고 통계는 그대로다. (REVIEW_NOT_FOUND)")
    void deleteAlreadyDeletedReview() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        mockMvc.perform(delete("/api/reviews/{id}", review1Id)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(delete("/api/reviews/{id}", review1Id)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));

        assertThat(testFixtures.reviewStatOf(productAId).reviewCount()).isEqualTo(2);
        assertThat(testFixtures.reviewStatOf(productAId).ratingSum()).isEqualTo(8);
        assertThat(testFixtures.reviewStatOf(productAId).rating(5)).isEqualTo(0);
    }

    @Test
    @DisplayName("같은 리뷰에 좋아요를 두 번 눌러도 한 번만 반영한다.")
    void likeTwiceCountsOnce() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        // When & Then
        mockMvc.perform(post("/api/reviews/{id}/likes", review1Id)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/reviews/{id}/likes", review1Id)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNoContent());

        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review1Id)).likerIds()).containsExactly(buyer2Id);
        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review1Id)).likeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요 취소를 두 번 보내도 좋아요 수는 0 아래로 내려가지 않는다.")
    void unlikeTwiceStaysAtZero() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        mockMvc.perform(post("/api/reviews/{id}/likes", review1Id)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(delete("/api/reviews/{id}/likes", review1Id)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/reviews/{id}/likes", review1Id)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNoContent());

        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review1Id)).likerIds()).isEmpty();
        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review1Id)).likeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("삭제한 리뷰에 좋아요를 실패한다. (REVIEW_NOT_FOUND)")
    void likeDeletedReview() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        mockMvc.perform(delete("/api/reviews/{id}", review1Id)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(post("/api/reviews/{id}/likes", review1Id)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));

        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review1Id)).likerIds()).isEmpty();
        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review1Id)).likeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("삭제한 리뷰의 좋아요 취소를 실패한다. (REVIEW_NOT_FOUND)")
    void unlikeDeletedReview() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        mockMvc.perform(post("/api/reviews/{id}/likes", review1Id)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/reviews/{id}", review1Id)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(delete("/api/reviews/{id}/likes", review1Id)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));

        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review1Id)).likerIds()).containsExactly(buyer2Id);
        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(review1Id)).likeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("리뷰 목록을 최신순 커서로 조회한다.")
    void getReviewsLatestByCursor() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        List<String> reviewIds = List.of(review1Id, review2Id, review3Id);

        Instant day = Instant.now().minus(10, ChronoUnit.DAYS);

        for (int i = 1; i <= 3; i++) {
            testFixtures.setReviewCreatedAt(UUID.fromString(reviewIds.get(i - 1)), day.plus(i, ChronoUnit.HOURS));
        }

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/products/{id}/reviews", productAId)
                        .param("sort", "latest")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(review3Id))
                .andExpect(jsonPath("$.items[0].rating").value(4))
                .andExpect(jsonPath("$.items[0].content").value("효과가 좋아요 정말 만족합니다"))
                .andExpect(jsonPath("$.items[0].likeCount").value(0))
                .andExpect(jsonPath("$.items[0].likedByMe").value(false))
                .andExpect(jsonPath("$.items[0].createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.items[0].memberId").doesNotExist())
                .andExpect(jsonPath("$.items[1].id").value(review2Id))
                .andExpect(jsonPath("$.items[1].rating").value(4))
                .andExpect(jsonPath("$.items[1].content").value("효과가 좋아요 정말 만족합니다"))
                .andExpect(jsonPath("$.items[1].likeCount").value(0))
                .andExpect(jsonPath("$.items[1].likedByMe").value(false))
                .andExpect(jsonPath("$.items[1].createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.nextCursor").value(notNullValue()))
                .andReturn();

        String nextCursor = JsonPath.read(result.getResponse().getContentAsString(), "$.nextCursor");

        mockMvc.perform(get("/api/products/{id}/reviews", productAId)
                        .param("sort", "latest")
                        .param("cursor", nextCursor)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(review1Id))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    @DisplayName("정렬을 생략하면 최신순으로 조회한다.")
    void defaultSortIsLatest() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        List<String> reviewIds = List.of(review1Id, review2Id, review3Id);

        Instant day = Instant.now().minus(10, ChronoUnit.DAYS);

        for (int i = 1; i <= 3; i++) {
            testFixtures.setReviewCreatedAt(UUID.fromString(reviewIds.get(i - 1)), day.plus(i, ChronoUnit.HOURS));
        }

        // When & Then
        mockMvc.perform(get("/api/products/{id}/reviews", productAId)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(review3Id))
                .andExpect(jsonPath("$.items[1].id").value(review2Id))
                .andExpect(jsonPath("$.nextCursor").value(notNullValue()));
    }

    @Test
    @DisplayName("리뷰 목록을 좋아요순으로 조회한다.")
    void getReviewsByLikes() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        List<String> reviewIds = List.of(review1Id, review2Id, review3Id);

        Instant day = Instant.now().minus(10, ChronoUnit.DAYS);

        for (int i = 1; i <= 3; i++) {
            testFixtures.setReviewCreatedAt(UUID.fromString(reviewIds.get(i - 1)), day.plus(i, ChronoUnit.HOURS));
        }

        mockMvc.perform(post("/api/reviews/{id}/likes", review1Id)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(get("/api/products/{id}/reviews", productAId)
                        .param("sort", "likes")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].id").value(review1Id))
                .andExpect(jsonPath("$.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.items[1].id").value(review3Id))
                .andExpect(jsonPath("$.items[1].likeCount").value(0))
                .andExpect(jsonPath("$.items[2].id").value(review2Id))
                .andExpect(jsonPath("$.items[2].likeCount").value(0))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    @DisplayName("목록의 likedByMe는 요청자 기준으로 채운다.")
    void likedByMeIsRequesterBased() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        List<String> reviewIds = List.of(review1Id, review2Id, review3Id);

        Instant day = Instant.now().minus(10, ChronoUnit.DAYS);

        for (int i = 1; i <= 3; i++) {
            testFixtures.setReviewCreatedAt(UUID.fromString(reviewIds.get(i - 1)), day.plus(i, ChronoUnit.HOURS));
        }

        mockMvc.perform(post("/api/reviews/{id}/likes", review2Id)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/reviews/{id}/likes", review1Id)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(get("/api/products/{id}/reviews", productAId)
                        .param("sort", "latest")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(review3Id))
                .andExpect(jsonPath("$.items[0].likedByMe").value(false))
                .andExpect(jsonPath("$.items[1].id").value(review2Id))
                .andExpect(jsonPath("$.items[1].likedByMe").value(true))
                .andExpect(jsonPath("$.items[2].id").value(review1Id))
                .andExpect(jsonPath("$.items[2].likedByMe").value(false));
    }

    @Test
    @DisplayName("삭제한 리뷰는 목록에서 빠진다.")
    void deletedReviewExcludedFromList() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        mockMvc.perform(delete("/api/reviews/{id}", review1Id)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(get("/api/products/{id}/reviews", productAId)
                        .param("sort", "latest")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[*].id").value(containsInAnyOrder(review3Id, review2Id)))
                .andExpect(jsonPath("$.items[*].id").value(not(hasItem(review1Id))));
    }

    @Test
    @DisplayName("리뷰가 없는 상품의 목록은 빈 목록이다.")
    void getReviewsOfProductWithoutReviews() throws Exception {
        // Given
        UUID productId = Ids.newId();

        // When & Then
        mockMvc.perform(get("/api/products/{id}/reviews", productId)
                        .param("sort", "latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("허용 밖 정렬 값으로 목록 조회를 실패한다. (INVALID_REQUEST)")
    void getReviewsWithInvalidSort() throws Exception {
        // Given
        UUID productId = Ids.newId();

        // When & Then
        mockMvc.perform(get("/api/products/{id}/reviews", productId)
                        .param("sort", "rating"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("리뷰가 없는 상품의 통계는 0건이고 평균은 null이다.")
    void statsOfProductWithoutReviews() throws Exception {
        // Given
        UUID productId = Ids.newId();

        // When & Then
        mockMvc.perform(get("/api/products/{id}/review-stats", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.average").value(nullValue()))
                .andExpect(jsonPath("$.distribution['1']").value(0))
                .andExpect(jsonPath("$.distribution['2']").value(0))
                .andExpect(jsonPath("$.distribution['3']").value(0))
                .andExpect(jsonPath("$.distribution['4']").value(0))
                .andExpect(jsonPath("$.distribution['5']").value(0));
    }

    @Test
    @DisplayName("리뷰가 모두 지워진 상품의 통계는 0건이고 평균은 null이다.")
    void statsOfProductWithAllReviewsDeleted() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");

        mockMvc.perform(delete("/api/reviews/{id}", review1Id)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(get("/api/products/{id}/review-stats", productAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.average").value(nullValue()))
                .andExpect(jsonPath("$.distribution['5']").value(0));
    }

    @Test
    @DisplayName("내 리뷰를 구매확정 품목 ID로 조회한다.")
    void getMine() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");

        testFixtures.setReviewCreatedAt(UUID.fromString(review1Id), Instant.parse("2026-10-01T00:00:00Z"));

        // When & Then
        mockMvc.perform(get("/api/reviews/mine")
                        .param("orderLineIds", orderLine1Id.toString(), orderLine2Id.toString())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reviewId").value(review1Id))
                .andExpect(jsonPath("$[0].orderLineId").value(orderLine1Id.toString()))
                .andExpect(jsonPath("$[0].productId").value(productAId.toString()))
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].content").value("효과가 좋아요 정말 만족합니다"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].editableUntil").value("2026-10-31T00:00:00Z"));
    }

    @Test
    @DisplayName("내 리뷰 조회는 삭제한 리뷰도 돌려준다.")
    void getMineIncludesDeletedReview() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        mockMvc.perform(delete("/api/reviews/{id}", review1Id)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(get("/api/reviews/mine")
                        .param("orderLineIds", orderLine1Id.toString())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reviewId").value(review1Id))
                .andExpect(jsonPath("$[0].status").value("DELETED"));
    }

    @Test
    @DisplayName("내 리뷰 조회에서 남의 품목은 빠진다.")
    void getMineExcludesOtherMembersLine() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        UUID buyer2Id = testFixtures.signup("buyer2@test.com");
        String token2 = testFixtures.login("buyer2@test.com");
        UUID orderLine2Id = testFixtures.createConfirmedLine(buyer2Id, sellerId, shopId, productAId);

        UUID buyer3Id = testFixtures.signup("buyer3@test.com");
        String token3 = testFixtures.login("buyer3@test.com");
        UUID orderLine3Id = testFixtures.createConfirmedLine(buyer3Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result1 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine2Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result2 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new WriteReviewRequest(orderLine3Id, 4, "효과가 좋아요 정말 만족합니다");

        MvcResult result3 = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String review1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.reviewId");
        String review2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.reviewId");
        String review3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.reviewId");

        // When & Then
        mockMvc.perform(get("/api/reviews/mine")
                        .param("orderLineIds", orderLine1Id.toString())
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @ParameterizedTest
    @MethodSource("getMineWithInvalidInputProvider")
    @DisplayName("품목 ID 누락·51개·UUID 형식 오류로 내 리뷰 조회를 실패한다. (INVALID_REQUEST)")
    void getMineWithInvalidInput(String orderLineIds) throws Exception {
        // Given
        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");

        // When & Then
        MockHttpServletRequestBuilder request = get("/api/reviews/mine")
                .header("Authorization", "Bearer " + token1);

        if (orderLineIds != null) {
            request.param("orderLineIds", orderLineIds);
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    public static Stream<Arguments> getMineWithInvalidInputProvider() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(Stream.generate(() -> Ids.newId().toString())
                        .limit(51)
                        .collect(Collectors.joining(","))),
                Arguments.of("not-a-uuid")
        );
    }

    @Test
    @DisplayName("품목 ID 50개까지 내 리뷰를 조회한다.")
    void getMineWithFiftyIds() throws Exception {
        // Given
        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");

        // When & Then
        mockMvc.perform(get("/api/reviews/mine")
                        .header("Authorization", "Bearer " + token1)
                        .param("orderLineIds", Stream.generate(() -> Ids.newId().toString())
                                .limit(50)
                                .collect(Collectors.joining(","))))
                .andExpect(status().isOk());
    }
}
