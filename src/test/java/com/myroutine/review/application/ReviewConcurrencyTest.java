package com.myroutine.review.application;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.review.web.EditReviewRequest;
import com.myroutine.review.web.WriteReviewRequest;
import com.myroutine.support.ConcurrencyRunner;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ReviewConcurrencyTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;

    @Autowired
    public ReviewConcurrencyTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("같은 품목에 리뷰를 동시에 작성하면 한 건만 성공한다.")
    void writeSameLineConcurrently() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        Callable<MvcResult> task = () -> mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        // When
        List<ConcurrencyRunner.Result<MvcResult>> results = ConcurrencyRunner.run(2, task);

        // Then
        List<MvcResult> responses = results.stream().map(ConcurrencyRunner.Result::value).toList();
        List<Integer> statuses = responses.stream().map(result -> result.getResponse().getStatus()).toList();
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);

        String reviewId = "";

        for (MvcResult response : responses) {
            if (response.getResponse().getStatus() == 409) {
                String code = JsonPath.read(response.getResponse().getContentAsString(), "$.code");
                assertThat(code).isIn("DUPLICATE_RESOURCE", "REVIEW_ALREADY_EXISTS");
            } else {
                reviewId = JsonPath.read(response.getResponse().getContentAsString(), "$.reviewId");
            }
        }

        assertThat(testFixtures.reviewCountOf(productAId)).isEqualTo(1);
        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(reviewId)).status()).isEqualTo("ACTIVE");
        assertThat(testFixtures.reviewStatOf(productAId).reviewCount()).isEqualTo(1);
        assertThat(testFixtures.reviewStatOf(productAId).ratingSum()).isEqualTo(5);
        assertThat(testFixtures.reviewStatOf(productAId).rating(5)).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 회원 100명이 동시에 좋아요를 누르면 100건이 모두 반영된다.")
    void likeConcurrentlyByDifferentMembers() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        String reviewId = JsonPath.read(result.getResponse().getContentAsString(), "$.reviewId");

        List<String> likerTokens = IntStream.range(1, 101)
                .mapToObj(i -> testFixtures.token("liker" + i + "@test.com"))
                .toList();

        List<Callable<MvcResult>> tasks = likerTokens.stream()
                .map(likerToken -> (Callable<MvcResult>) () -> mockMvc.perform(MockMvcRequestBuilders.post("/api/reviews/{id}/likes", reviewId)
                                .header("Authorization", "Bearer " + likerToken))
                        .andReturn())
                .toList();

        // When
        List<ConcurrencyRunner.Result<MvcResult>> results = ConcurrencyRunner.run(tasks);

        // Then
        List<MvcResult> responses = results.stream().map(ConcurrencyRunner.Result::value).toList();
        List<Integer> statuses = responses.stream().map(r -> r.getResponse().getStatus()).toList();
        assertThat(statuses).containsOnly(204);
        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(reviewId)).likerIds().size()).isEqualTo(100);
        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(reviewId)).likeCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("서로 다른 품목의 리뷰 50건을 동시에 작성하면 통계에 50건이 모두 반영된다.")
    void writeDifferentLinesConcurrently() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);


        List<Callable<MvcResult>> tasks = new ArrayList<>();

        for (int i = 1; i <= 50; i++) {
            UUID buyerId = testFixtures.signup("buyer" + i + "@test.com");
            String token = testFixtures.login("buyer" + i + "@test.com");
            UUID orderLineId = testFixtures.createConfirmedLine(buyerId, sellerId, shopId, productAId);

            WriteReviewRequest request;

            if (i <= 20) {
                request = new WriteReviewRequest(orderLineId, 5, "효과가 좋아요 정말 만족합니다");
            } else if (i <= 35) {
                request = new WriteReviewRequest(orderLineId, 4, "효과가 좋아요 정말 만족합니다");
            } else if (i <= 45) {
                request = new WriteReviewRequest(orderLineId, 3, "효과가 좋아요 정말 만족합니다");
            } else {
                request = new WriteReviewRequest(orderLineId, 1, "효과가 좋아요 정말 만족합니다");
            }
            Callable<MvcResult> task = () -> mockMvc.perform(post("/api/reviews")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();
            tasks.add(task);
        }

        // When
        List<ConcurrencyRunner.Result<MvcResult>> results = ConcurrencyRunner.run(tasks);

        // Then
        List<MvcResult> responses = results.stream().map(ConcurrencyRunner.Result::value).toList();
        List<Integer> statuses = responses.stream().map(r -> r.getResponse().getStatus()).toList();
        assertThat(statuses).containsOnly(201);
        assertThat(testFixtures.reviewCountOf(productAId)).isEqualTo(50);
        assertThat(testFixtures.reviewStatOf(productAId).reviewCount()).isEqualTo(50);
        assertThat(testFixtures.reviewStatOf(productAId).ratingSum()).isEqualTo(195);
        assertThat(testFixtures.reviewStatOf(productAId).rating(1)).isEqualTo(5);
        assertThat(testFixtures.reviewStatOf(productAId).rating(3)).isEqualTo(10);
        assertThat(testFixtures.reviewStatOf(productAId).rating(4)).isEqualTo(15);
        assertThat(testFixtures.reviewStatOf(productAId).rating(5)).isEqualTo(20);

        mockMvc.perform(get("/api/products/{id}/review-stats", productAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value(3.9));
    }

    @Test
    @DisplayName("리뷰를 수정하는 동안 좋아요가 들어와도 좋아요 수가 그대로 반영된다.")
    void editWhileLikingConcurrently() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productAId = testFixtures.registerProduct(sellerId, shopId, 10000, 100);

        UUID buyer1Id = testFixtures.signup("buyer1@test.com");
        String token1 = testFixtures.login("buyer1@test.com");
        UUID orderLine1Id = testFixtures.createConfirmedLine(buyer1Id, sellerId, shopId, productAId);

        WriteReviewRequest request = new WriteReviewRequest(orderLine1Id, 5, "효과가 좋아요 정말 만족합니다");

        MvcResult result = mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        String reviewId = JsonPath.read(result.getResponse().getContentAsString(), "$.reviewId");

        List<String> tokens = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            UUID buyerId = testFixtures.signup("liker" + i + "@test.com");
            String token = testFixtures.login("liker" + i + "@test.com");
            tokens.add(token);
        }

        List<Callable<MvcResult>> tasks = new ArrayList<>();

        Callable<MvcResult> patchTask = () -> mockMvc.perform(patch("/api/reviews/{id}", reviewId)
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditReviewRequest(4, "수정한 리뷰 내용입니다."))))
                .andReturn();

        tasks.add(patchTask);

        List<Callable<MvcResult>> likeTasks = tokens.stream()
                .map(token -> (Callable<MvcResult>) () -> mockMvc.perform(MockMvcRequestBuilders.post("/api/reviews/{id}/likes", reviewId)
                                .header("Authorization", "Bearer " + token))
                        .andReturn())
                .toList();

        tasks.addAll(likeTasks);

        // When
        List<ConcurrencyRunner.Result<MvcResult>> results = ConcurrencyRunner.run(tasks);

        MvcResult patchResult = results.getFirst().value();
        assertThat(patchResult.getResponse().getStatus()).isEqualTo(200);

        List<MvcResult> likeResults = results.subList(1, 51).stream()
                .map(ConcurrencyRunner.Result::value)
                .toList();

        assertThat(likeResults).allMatch(r -> r.getResponse().getStatus() == 204);

        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(reviewId)).rating()).isEqualTo(4);
        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(reviewId)).content()).isEqualTo("수정한 리뷰 내용입니다.");
        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(reviewId)).likerIds().size()).isEqualTo(50);
        assertThat(testFixtures.reviewSnapshotOf(UUID.fromString(reviewId)).likeCount()).isEqualTo(50);
    }

}
