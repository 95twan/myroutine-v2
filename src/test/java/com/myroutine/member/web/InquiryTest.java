package com.myroutine.member.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.model.Ids;
import com.myroutine.support.InquirySnapshot;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InquiryTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    public InquiryTest(TestFixtures testFixtures, MockMvc mockMvc, ObjectMapper objectMapper) {
        this.testFixtures = testFixtures;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("문의를 작성한다.")
    void write() throws Exception {
        // Given
        UUID user1Id = testFixtures.signup("user1@test.com");
        String user1token = testFixtures.login("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        // When & Then
        mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inquiryId").isNotEmpty());

        assertThat(testFixtures.inquiriesOf(user1Id)).hasSize(1);
        assertThat(testFixtures.inquiriesOf(user1Id).getFirst().memberId()).isEqualTo(user1Id);
        assertThat(testFixtures.inquiriesOf(user1Id).getFirst().title()).isEqualTo("배송이 늦어요");
        assertThat(testFixtures.inquiriesOf(user1Id).getFirst().content()).isEqualTo("주문한 상품이 아직 도착하지 않았습니다.");
        assertThat(testFixtures.inquiriesOf(user1Id).getFirst().status()).isEqualTo("OPEN");
    }

    @ParameterizedTest
    @MethodSource("writeWithInvalidInputFailsProvider")
    @DisplayName("문의 작성을 실패한다. (제목·본문 길이 위반, INVALID_REQUEST)")
    void writeWithInvalidInputFails(String title, String content) throws Exception {
        // Given
        UUID user1Id = testFixtures.signup("user1@test.com");
        String user1token = testFixtures.login("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // When & Then
        mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(testFixtures.inquiriesOf(user1Id)).hasSize(1);

        request = new WriteInquiryRequest("가".repeat(100), "가".repeat(2000));

        mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    public static Stream<Arguments> writeWithInvalidInputFailsProvider() {
        return Stream.of(
                Arguments.of("", "주문한 상품이 아직 도착하지 않았습니다."),
                Arguments.of("가".repeat(101), "주문한 상품이 아직 도착하지 않았습니다."),
                Arguments.of("배송이 늦어요", ""),
                Arguments.of("배송이 늦어요", "가".repeat(2001))
        );
    }

    @Test
    @DisplayName("본인 문의 목록은 최신순이다.")
    void listMineLatestFirst() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result3 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");
        String inquiry2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.inquiryId");
        String inquiry3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.inquiryId");

        // When & Then
        mockMvc.perform(get("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(inquiry3Id))
                .andExpect(jsonPath("$.items[1].id").value(inquiry2Id))
                .andExpect(jsonPath("$.items[2].id").value(inquiry1Id));
    }

    @Test
    @DisplayName("본인 문의 목록 항목에 답변 시각이 담긴다.")
    void listMineHasAnsweredAt() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");
        String inquiry2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.inquiryId");

        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        mockMvc.perform(post("/admin/api/inquiries/{id}/answer", inquiry2Id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerInquiryRequest("확인 후 오늘 중 안내드리겠습니다."))))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(get("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(inquiry2Id))
                .andExpect(jsonPath("$.items[0].answeredAt").value(notNullValue()))
                .andExpect(jsonPath("$.items[1].id").value(inquiry1Id))
                .andExpect(jsonPath("$.items[1].answeredAt").value(nullValue()));
    }

    @Test
    @DisplayName("남의 문의 조회를 실패한다. (남의 문의·없는 문의, INQUIRY_NOT_FOUND)")
    void getOthersInquiryFails() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");

        String user2token = testFixtures.token("user2@test.com");

        // When & Then
        mockMvc.perform(get("/api/inquiries/{id}", inquiry1Id)
                        .header("Authorization", "Bearer " + user2token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));

        mockMvc.perform(get("/api/inquiries/{id}", Ids.newId())
                        .header("Authorization", "Bearer " + user2token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));

        mockMvc.perform(get("/api/inquiries")
                        .header("Authorization", "Bearer " + user2token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(0)))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        mockMvc.perform(get("/api/inquiries/{id}", inquiry1Id)
                        .header("Authorization", "Bearer " + user1token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("배송이 늦어요"));

        mockMvc.perform(get("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(inquiry1Id));
    }

    @Test
    @DisplayName("관리자가 답변하면 문의가 ANSWERED가 된다.")
    void answerChangesStatusToAnswered() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");

        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        // When & Then
        mockMvc.perform(post("/admin/api/inquiries/{id}/answer", inquiry1Id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerInquiryRequest("확인 후 오늘 중 안내드리겠습니다."))))
                .andExpect(status().isNoContent());

        InquirySnapshot i = testFixtures.inquiryOf(UUID.fromString(inquiry1Id));
        assertThat(i.status()).isEqualTo("ANSWERED");
        assertThat(i.answers()).hasSize(1);
        assertThat(i.answers().getFirst().adminId()).isEqualTo(adminId);
        assertThat(i.answers().getFirst().content()).isEqualTo("확인 후 오늘 중 안내드리겠습니다.");

        mockMvc.perform(get("/api/inquiries/{id}", inquiry1Id)
                        .header("Authorization", "Bearer " + user1token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.answer.content").value("확인 후 오늘 중 안내드리겠습니다."))
                .andExpect(jsonPath("$.answer.adminId").doesNotExist());
    }

    @Test
    @DisplayName("답변을 실패한다. (본문 비어 있음·길이 초과, INVALID_REQUEST)")
    void answerWithInvalidInputFails() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");

        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        // When & Then
        mockMvc.perform(post("/admin/api/inquiries/{id}/answer", inquiry1Id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerInquiryRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/admin/api/inquiries/{id}/answer", inquiry1Id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerInquiryRequest("가".repeat(2001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("답변을 실패한다. (이미 답변한 문의, INVALID_STATE_TRANSITION)")
    void answerTwiceFails() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");

        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        mockMvc.perform(post("/admin/api/inquiries/{id}/answer", inquiry1Id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerInquiryRequest("확인 후 오늘 중 안내드리겠습니다."))))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(post("/admin/api/inquiries/{id}/answer", inquiry1Id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerInquiryRequest("추가 안내입니다."))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        assertThat(testFixtures.inquiryOf(UUID.fromString(inquiry1Id)).answers()).hasSize(1);
        assertThat(testFixtures.inquiryOf(UUID.fromString(inquiry1Id)).answers().getFirst().content()).isEqualTo("확인 후 오늘 중 안내드리겠습니다.");
        assertThat(testFixtures.inquiryOf(UUID.fromString(inquiry1Id)).status()).isEqualTo("ANSWERED");
    }

    @Test
    @DisplayName("답변을 실패한다. (없는 문의, INQUIRY_NOT_FOUND)")
    void answerUnknownInquiryFails() throws Exception {
        // Given
        UUID inquiry1Id = Ids.newId();

        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        // When & Then
        mockMvc.perform(post("/admin/api/inquiries/{id}/answer", inquiry1Id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerInquiryRequest("확인 후 오늘 중 안내드리겠습니다."))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
    }

    @Test
    @DisplayName("관리자 문의 목록을 상태로 거른다.")
    void adminListFilterByStatus() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result3 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");
        String inquiry2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.inquiryId");
        String inquiry3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.inquiryId");

        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        mockMvc.perform(post("/admin/api/inquiries/{id}/answer", inquiry2Id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerInquiryRequest("확인 후 오늘 중 안내드리겠습니다."))))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(get("/admin/api/inquiries")
                        .param("status", "OPEN")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(2)))
                .andExpect(jsonPath("$.items[*].id").value(containsInAnyOrder(inquiry1Id, inquiry3Id)))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        mockMvc.perform(get("/admin/api/inquiries")
                        .param("status", "ANSWERED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(inquiry2Id));
    }

    @Test
    @DisplayName("관리자 문의 목록은 오래된 문의부터 다음 쪽으로 이어진다.")
    void adminListCursorPagingOldestFirst() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result3 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");
        String inquiry2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.inquiryId");
        String inquiry3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.inquiryId");

        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        // When & Then
        MvcResult result = mockMvc.perform(get("/admin/api/inquiries")
                        .param("status", "OPEN")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(inquiry1Id))
                .andExpect(jsonPath("$.items[1].id").value(inquiry2Id))
                .andExpect(jsonPath("$.nextCursor").value(notNullValue()))
                .andReturn();

        String nextCursor = JsonPath.read(result.getResponse().getContentAsString(), "$.nextCursor");
        mockMvc.perform(get("/admin/api/inquiries")
                        .param("status", "OPEN")
                        .param("size", "2")
                        .param("cursor", nextCursor)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(inquiry3Id))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    @DisplayName("관리자 문의 목록은 상태를 생략하면 전체다.")
    void adminListWithoutStatusReturnsAll() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");
        String inquiry2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.inquiryId");

        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        mockMvc.perform(post("/admin/api/inquiries/{id}/answer", inquiry2Id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerInquiryRequest("확인 후 오늘 중 안내드리겠습니다."))))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(get("/admin/api/inquiries")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(2)))
                .andExpect(jsonPath("$.items[*].id").value(containsInAnyOrder(inquiry1Id, inquiry2Id)));
    }

    @Test
    @DisplayName("관리자 문의 목록 항목에 작성자·본문·답변이 담긴다.")
    void adminListItemHasAuthorContentAnswer() throws Exception {
        // Given
        UUID user1Id = testFixtures.signup("user1@test.com");
        String user1token = testFixtures.login("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");

        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        mockMvc.perform(post("/admin/api/inquiries/{id}/answer", inquiry1Id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerInquiryRequest("확인 후 오늘 중 안내드리겠습니다."))))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(get("/admin/api/inquiries")
                        .param("status", "ANSWERED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(inquiry1Id))
                .andExpect(jsonPath("$.items[0].memberId").value(user1Id.toString()))
                .andExpect(jsonPath("$.items[0].memberEmail").value("user1@test.com"))
                .andExpect(jsonPath("$.items[0].memberNickname").value("user1"))
                .andExpect(jsonPath("$.items[0].content").value("주문한 상품이 아직 도착하지 않았습니다."))
                .andExpect(jsonPath("$.items[0].answer.adminId").value(adminId.toString()))
                .andExpect(jsonPath("$.items[0].answer.content").value("확인 후 오늘 중 안내드리겠습니다."));
    }

    @Test
    @DisplayName("관리자 API 호출을 실패한다. (일반 회원, FORBIDDEN)")
    void adminApiByMemberFails() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");

        // When & Then
        mockMvc.perform(get("/admin/api/inquiries")
                        .param("status", "OPEN")
                        .header("Authorization", "Bearer " + user1token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/admin/api/inquiries/{id}/answer", inquiry1Id)
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerInquiryRequest("확인 후 오늘 중 안내드리겠습니다."))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("답변 전 문의를 수정한다.")
    void editBeforeAnswer() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");

        // When & Then
        mockMvc.perform(patch("/api/inquiries/{id}", inquiry1Id)
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditInquiryRequest("배송이 많이 늦어요", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("배송이 많이 늦어요"))
                .andExpect(jsonPath("$.content").value("주문한 상품이 아직 도착하지 않았습니다."))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.answer").value(nullValue()));

        assertThat(testFixtures.inquiryOf(UUID.fromString(inquiry1Id)).title()).isEqualTo("배송이 많이 늦어요");
        assertThat(testFixtures.inquiryOf(UUID.fromString(inquiry1Id)).content()).isEqualTo("주문한 상품이 아직 도착하지 않았습니다.");
        assertThat(testFixtures.inquiryOf(UUID.fromString(inquiry1Id)).status()).isEqualTo("OPEN");
    }

    @ParameterizedTest
    @MethodSource("editWithInvalidInputFailsProvider")
    @DisplayName("문의 수정을 실패한다. (제목·본문 형식 위반, INVALID_REQUEST)")
    void editWithInvalidInputFails(String title, String content) throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");

        // When & Then
        mockMvc.perform(patch("/api/inquiries/{id}", inquiry1Id)
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditInquiryRequest(title, content))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(testFixtures.inquiryOf(UUID.fromString(inquiry1Id)).title()).isEqualTo("배송이 늦어요");
        assertThat(testFixtures.inquiryOf(UUID.fromString(inquiry1Id)).content()).isEqualTo("주문한 상품이 아직 도착하지 않았습니다.");

        mockMvc.perform(patch("/api/inquiries/{id}", inquiry1Id)
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditInquiryRequest("가".repeat(100), "가".repeat(2000)))))
                .andExpect(status().isOk());
    }

    public static Stream<Arguments> editWithInvalidInputFailsProvider() {
        return Stream.of(
                Arguments.of("", "주문한 상품이 1주일이 지나도 도착하지 않았습니다."),
                Arguments.of("   ", "주문한 상품이 1주일이 지나도 도착하지 않았습니다."),
                Arguments.of("가".repeat(101), "주문한 상품이 1주일이 지나고 도착하지 않았습니다."),
                Arguments.of("배송이 많이 늦어요", ""),
                Arguments.of("배송이 많이 늦어요", "가".repeat(2001))
        );
    }

    @Test
    @DisplayName("문의를 여러 줄 내용으로 수정한다.")
    void editWithMultilineContent() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");

        // When & Then
        mockMvc.perform(patch("/api/inquiries/{id}", inquiry1Id)
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditInquiryRequest(null, "첫 줄\n둘째 줄"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("첫 줄\n둘째 줄"));

        assertThat(testFixtures.inquiryOf(UUID.fromString(inquiry1Id)).content()).isEqualTo("첫 줄\n둘째 줄");
    }

    @Test
    @DisplayName("문의 수정을 실패한다. (공백과 줄바꿈만 있는 내용, INVALID_REQUEST)")
    void editWithBlankMultilineContentFails() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");

        // When & Then
        mockMvc.perform(patch("/api/inquiries/{id}", inquiry1Id)
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditInquiryRequest(null, " \n "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details.content").value("공백만 입력할 수 없습니다."));

        assertThat(testFixtures.inquiryOf(UUID.fromString(inquiry1Id)).content()).isEqualTo("주문한 상품이 아직 도착하지 않았습니다.");
    }

    @Test
    @DisplayName("문의 수정을 실패한다. (줄바꿈이 있는 제목, INVALID_REQUEST)")
    void editWithMultilineTitleFails() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");

        // When & Then
        mockMvc.perform(patch("/api/inquiries/{id}", inquiry1Id)
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditInquiryRequest("첫 줄\n둘째 줄", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details.title").value("공백만 입력할 수 없습니다."));

        assertThat(testFixtures.inquiryOf(UUID.fromString(inquiry1Id)).title()).isEqualTo("배송이 늦어요");
    }

    @Test
    @DisplayName("남의 문의 수정·삭제를 실패한다. (남의 문의·없는 문의, INQUIRY_NOT_FOUND)")
    void editOrDeleteOthersInquiryFails() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");

        UUID user2Id = testFixtures.signup("user2@test.com");
        String user2token = testFixtures.login("user2@test.com");

        // When & Then
        mockMvc.perform(patch("/api/inquiries/{id}", inquiry1Id)
                        .header("Authorization", "Bearer " + user2token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditInquiryRequest("바꾼 제목", null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));

        mockMvc.perform(delete("/api/inquiries/{id}", inquiry1Id)
                        .header("Authorization", "Bearer " + user2token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));

        mockMvc.perform(delete("/api/inquiries/{id}", Ids.newId())
                        .header("Authorization", "Bearer " + user1token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));

        assertThat(testFixtures.inquiryOf(UUID.fromString(inquiry1Id)).title()).isEqualTo("배송이 늦어요");
        assertThat(testFixtures.inquiryOf(UUID.fromString(inquiry1Id)).content()).isEqualTo("주문한 상품이 아직 도착하지 않았습니다.");
    }

    @Test
    @DisplayName("답변 전 문의를 삭제한다.")
    void deleteBeforeAnswer() throws Exception {
        // Given
        UUID user1Id = testFixtures.signup("user1@test.com");
        String user1token = testFixtures.login("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");

        // When & Then
        mockMvc.perform(delete("/api/inquiries/{id}", inquiry1Id)
                        .header("Authorization", "Bearer " + user1token))
                .andExpect(status().isNoContent());

        assertThat(testFixtures.inquiriesOf(user1Id)).hasSize(0);

        mockMvc.perform(get("/api/inquiries/{id}", inquiry1Id)
                        .header("Authorization", "Bearer " + user1token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INQUIRY_NOT_FOUND"));
    }

    @Test
    @DisplayName("답변된 문의 수정·삭제를 실패한다. (INQUIRY_ALREADY_ANSWERED)")
    void editOrDeleteAnsweredInquiryFails() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");

        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        mockMvc.perform(post("/admin/api/inquiries/{id}/answer", inquiry1Id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerInquiryRequest("확인 후 오늘 중 안내드리겠습니다."))))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(patch("/api/inquiries/{id}", inquiry1Id)
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditInquiryRequest("바꾼 제목", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INQUIRY_ALREADY_ANSWERED"));

        mockMvc.perform(delete("/api/inquiries/{id}", inquiry1Id)
                        .header("Authorization", "Bearer " + user1token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INQUIRY_ALREADY_ANSWERED"));

        assertThat(testFixtures.inquiryOf(UUID.fromString(inquiry1Id)).title()).isEqualTo("배송이 늦어요");
        assertThat(testFixtures.inquiryOf(UUID.fromString(inquiry1Id)).status()).isEqualTo("ANSWERED");
        assertThat(testFixtures.inquiryOf(UUID.fromString(inquiry1Id)).answers()).hasSize(1);
    }

    @Test
    @DisplayName("본인 문의 목록은 커서로 다음 페이지를 조회한다.")
    void listMineNextPage() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result3 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");
        String inquiry2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.inquiryId");
        String inquiry3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.inquiryId");

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/inquiries")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + user1token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(inquiry3Id))
                .andExpect(jsonPath("$.items[1].id").value(inquiry2Id))
                .andExpect(jsonPath("$.nextCursor").value(notNullValue()))
                .andReturn();

        String nextCursor = JsonPath.read(result.getResponse().getContentAsString(), "$.nextCursor");
        mockMvc.perform(get("/api/inquiries")
                        .param("size", "2")
                        .param("cursor", nextCursor)
                        .header("Authorization", "Bearer " + user1token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(inquiry1Id))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    @DisplayName("상태를 생략한 관리자 문의 목록은 커서로 다음 페이지를 조회한다.")
    void adminListWithoutStatusNextPage() throws Exception {
        // Given
        String user1token = testFixtures.token("user1@test.com");

        WriteInquiryRequest request = new WriteInquiryRequest("배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        MvcResult result1 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result3 = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", "Bearer " + user1token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String inquiry1Id = JsonPath.read(result1.getResponse().getContentAsString(), "$.inquiryId");
        String inquiry2Id = JsonPath.read(result2.getResponse().getContentAsString(), "$.inquiryId");
        String inquiry3Id = JsonPath.read(result3.getResponse().getContentAsString(), "$.inquiryId");

        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String adminToken = testFixtures.login("admin@test.com");

        // When & Then
        MvcResult result = mockMvc.perform(get("/admin/api/inquiries")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(inquiry1Id))
                .andExpect(jsonPath("$.items[1].id").value(inquiry2Id))
                .andExpect(jsonPath("$.nextCursor").value(notNullValue()))
                .andReturn();

        String nextCursor = JsonPath.read(result.getResponse().getContentAsString(), "$.nextCursor");
        mockMvc.perform(get("/admin/api/inquiries")
                        .param("size", "2")
                        .param("cursor", nextCursor)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(inquiry3Id))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }
}
