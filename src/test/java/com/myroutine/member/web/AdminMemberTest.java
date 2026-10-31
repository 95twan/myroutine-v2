package com.myroutine.member.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.model.Ids;
import com.myroutine.common.outbox.OutboxRelay;
import com.myroutine.member.api.event.MemberTopics;
import com.myroutine.member.application.TokenResult;
import com.myroutine.member.domain.RefreshToken;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminMemberTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxAssert outboxAssert;
    private final OutboxRelay outboxRelay;

    @Autowired
    public AdminMemberTest(MockMvc mockMvc, TestFixtures testFixtures, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, OutboxAssert outboxAssert, OutboxRelay outboxRelay) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.outboxAssert = outboxAssert;
        this.outboxRelay = outboxRelay;
    }

    @Test
    @DisplayName("관리자 API 호출을 실패한다. (일반 회원, FORBIDDEN)")
    void adminApiWithMemberTokenFails() throws Exception {
        // Given
        testFixtures.signup("alan@test.com");

        UUID bobId = testFixtures.signup("bob@test.com");
        jdbcTemplate.update("UPDATE member.member SET nickname = 'zebra' WHERE id = ?", bobId);
        testFixtures.changeStatus(bobId, "BANNED");

        testFixtures.signup("carol@test.com");

        testFixtures.adminToken("admin@test.com");

        testFixtures.signup("alice@test.com");
        String token = testFixtures.login("alice@test.com");

        // When & Then
        mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/admin/api/members/{bobId}/unban", bobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(testFixtures.memberSnapshotOf(bobId).status()).isEqualTo("BANNED");
    }

    @Test
    @DisplayName("이메일 또는 닉네임 앞부분으로 회원을 검색한다.")
    void searchByPrefix() throws Exception {
        // Given
        testFixtures.signup("alan@test.com");

        UUID bobId = testFixtures.signup("bob@test.com");
        jdbcTemplate.update("UPDATE member.member SET nickname = 'zebra' WHERE id = ?", bobId);
        testFixtures.changeStatus(bobId, "BANNED");

        testFixtures.signup("carol@test.com");

        String adminToken = testFixtures.adminToken("admin@test.com");

        testFixtures.signup("alice@test.com");

        // When & Then
        mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("query", "al"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].email").value(containsInAnyOrder("alice@test.com", "alan@test.com")));

        mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("query", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].email").value(contains("bob@test.com")));

        mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("query", "zeb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].email").value(contains("bob@test.com")));

        mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("query", "ice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*]").value(hasSize(0)));

        mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("query", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*]").value(hasSize(0)));

        mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("query", "AL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].email").value(containsInAnyOrder("alice@test.com", "alan@test.com")));
    }

    @Test
    @DisplayName("커서로 회원 목록을 페이지별로 조회한다.")
    void listWithCursorPaging() throws Exception {
        // Given
        UUID alanId = testFixtures.signup("alan@test.com");

        UUID bobId = testFixtures.signup("bob@test.com");
        jdbcTemplate.update("UPDATE member.member SET nickname = 'zebra' WHERE id = ?", bobId);
        testFixtures.changeStatus(bobId, "BANNED");

        UUID carolId = testFixtures.signup("carol@test.com");

        String adminToken = testFixtures.adminToken("admin@test.com");
        UUID adminId = testFixtures.memberIdOf("admin@test.com");

        UUID aliceId = testFixtures.signup("alice@test.com");

        List<String> expectedIds = List.of(alanId.toString(), bobId.toString(), carolId.toString(), aliceId.toString(), adminId.toString());

        List<String> ids = new ArrayList<>();

        // When & Then
        MvcResult result = mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andReturn();

        ids.addAll(JsonPath.read(result.getResponse().getContentAsString(), "$.items[*].id"));
        String nextCursor = JsonPath.read(result.getResponse().getContentAsString(), "$.nextCursor");

        result = mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("cursor", nextCursor)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andReturn();

        ids.addAll(JsonPath.read(result.getResponse().getContentAsString(), "$.items[*].id"));
        nextCursor = JsonPath.read(result.getResponse().getContentAsString(), "$.nextCursor");

        result = mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("cursor", nextCursor)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andReturn();

        ids.addAll(JsonPath.read(result.getResponse().getContentAsString(), "$.items[*].id"));
        nextCursor = JsonPath.read(result.getResponse().getContentAsString(), "$.nextCursor");

        assertThat(nextCursor).isNull();

        assertThat(ids).hasSize(5);
        assertThat(ids).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(expectedIds);
    }

    @Test
    @DisplayName("회원을 제재한 뒤 해제하면 다시 로그인한다.")
    void banThenUnban() throws Exception {
        // Given
        String adminToken = testFixtures.adminToken("admin@test.com");

        UUID aliceId = testFixtures.signup("alice@test.com");
        TokenResult tokenResult = testFixtures.loginTokens("alice@test.com");
        UUID sid = RefreshToken.parse(tokenResult.refreshToken()).sessionId();

        BanRequest request = new BanRequest("운영정책 위반");

        // When & Then
        mockMvc.perform(post("/admin/api/members/{aliceId}/ban", aliceId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        MemberSnapshot m = testFixtures.memberSnapshotOf(aliceId);
        assertThat(m.status()).isEqualTo("BANNED");
        assertThat(m.banReason()).isEqualTo("운영정책 위반");
        assertThat(m.bannedAt()).isNotNull();

        assertThat(testFixtures.sessionSnapshotOf(sid).exists()).isFalse();
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_BANNED)).hasSize(1);
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_BANNED).getFirst().aggregateId()).isEqualTo(aliceId);
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_BANNED).getFirst().payload().get("memberId").asString()).isEqualTo(aliceId.toString());
        assertThat(outboxAssert.rows(MemberTopics.MEMBER_BANNED).getFirst().payload().get("reason").asString()).isEqualTo("운영정책 위반");

        mockMvc.perform(post("/admin/api/members/{aliceId}/unban", aliceId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        m = testFixtures.memberSnapshotOf(aliceId);
        assertThat(m.status()).isEqualTo("ACTIVE");
        assertThat(m.banReason()).isNull();
        assertThat(m.bannedAt()).isNull();


        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice@test.com", "pass1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    @DisplayName("제재·해제를 실패한다. (상태 전이 규칙 위반, INVALID_STATE_TRANSITION)")
    void banOrUnbanWithInvalidStateFails() throws Exception {
        // Given
        String adminToken = testFixtures.adminToken("admin@test.com");

        UUID bobId = testFixtures.signup("bob@test.com");
        jdbcTemplate.update("UPDATE member.member SET nickname = 'zebra' WHERE id = ?", bobId);
        testFixtures.changeStatus(bobId, "BANNED");

        UUID aliceId = testFixtures.signup("alice@test.com");

        BanRequest request = new BanRequest("중복");

        // When & Then
        mockMvc.perform(post("/admin/api/members/{bobId}/ban", bobId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        assertThat(outboxAssert.rows(MemberTopics.MEMBER_BANNED)).hasSize(0);

        mockMvc.perform(post("/admin/api/members/{aliceId}/unban", aliceId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("관리자 API 호출을 실패한다. (토큰 없음, UNAUTHORIZED)")
    void adminApiWithoutTokenFails() throws Exception {
        // Given

        // When & Then
        mockMvc.perform(get("/admin/api/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("상태로 회원 목록을 거른다.")
    void filterByStatus() throws Exception {
        // Given
        testFixtures.signup("alan@test.com");

        UUID bobId = testFixtures.signup("bob@test.com");
        jdbcTemplate.update("UPDATE member.member SET nickname = 'zebra' WHERE id = ?", bobId);
        testFixtures.changeStatus(bobId, "BANNED");

        testFixtures.signup("carol@test.com");

        String adminToken = testFixtures.adminToken("admin@test.com");

        testFixtures.signup("alice@test.com");

        // When & Then
        mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "BANNED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].email").value(contains("bob@test.com")));

        mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(4)));

        mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(5)));
    }

    @Test
    @DisplayName("회원 목록 항목에 역할이 담긴다.")
    void listItemHasRole() throws Exception {
        // Given
        String adminToken = testFixtures.adminToken("admin@test.com");

        testFixtures.signup("alice@test.com");

        // When & Then
        mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.email == 'alice@test.com')].role").value("USER"))
                .andExpect(jsonPath("$.items[?(@.email == 'admin@test.com')].role").value("ADMIN"));
    }

    @Test
    @DisplayName("회원을 제재하면 BANNED 알림이 만들어진다.")
    void banCreatesBannedNotification() throws Exception {
        // Given
        String adminToken = testFixtures.adminToken("admin@test.com");

        UUID aliceId = testFixtures.signup("alice@test.com");

        BanRequest request = new BanRequest("운영정책 위반");

        // When & Then
        mockMvc.perform(post("/admin/api/members/{aliceId}/ban", aliceId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        outboxRelay.relayOnce();

        Eventually.await(Duration.ofSeconds(5), () -> {
            List<NotificationRow> rows = testFixtures.notificationRowsOf(aliceId);
            assertThat(rows).filteredOn(row -> row.type().equals("BANNED")).hasSize(1);
        });
    }

    @Test
    @DisplayName("제재를 실패한다. (없는 회원, MEMBER_NOT_FOUND)")
    void banUnknownMemberFails() throws Exception {
        // Given
        String adminToken = testFixtures.adminToken("admin@test.com");

        BanRequest request = new BanRequest("운영정책 위반");

        // When & Then
        mockMvc.perform(post("/admin/api/members/{aliceId}/ban", Ids.newId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("제재를 실패한다. (사유 비어 있음, INVALID_REQUEST)")
    void banWithBlankReasonFails() throws Exception {
        // Given
        String adminToken = testFixtures.adminToken("admin@test.com");

        UUID aliceId = testFixtures.signup("alice@test.com");

        // When & Then
        mockMvc.perform(post("/admin/api/members/{aliceId}/ban", aliceId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(testFixtures.memberSnapshotOf(aliceId).status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("회원 목록 항목에 제재 사유가 담긴다.")
    void listItemHasBanReason() throws Exception {
        // Given
        String adminToken = testFixtures.adminToken("admin@test.com");

        UUID aliceId = testFixtures.signup("alice@test.com");
        testFixtures.signup("carol@test.com");

        BanRequest request = new BanRequest("운영정책 위반");

        mockMvc.perform(post("/admin/api/members/{aliceId}/ban", aliceId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.email == 'alice@test.com')].banReason").value("운영정책 위반"))
                .andExpect(jsonPath("$.items[?(@.email == 'carol@test.com')].banReason").value(hasItem(nullValue())));
    }

    @Test
    @DisplayName("제재된 회원의 로그인 거절 응답에 제재 사유가 담긴다.")
    void bannedLoginRejectionHasReason() throws Exception {
        // Given
        String adminToken = testFixtures.adminToken("admin@test.com");

        UUID aliceId = testFixtures.signup("alice@test.com");


        mockMvc.perform(post("/admin/api/members/{aliceId}/ban", aliceId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BanRequest("운영정책 위반"))))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice@test.com", "pass1234"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_BANNED"))
                .andExpect(jsonPath("$.details.reason").value("운영정책 위반"));
    }

    @Test
    @DisplayName("회원 목록 항목에 제재 시각이 담긴다.")
    void listItemHasBannedAt() throws Exception {
        // Given
        String adminToken = testFixtures.adminToken("admin@test.com");

        UUID aliceId = testFixtures.signup("alice@test.com");
        testFixtures.signup("carol@test.com");

        BanRequest request = new BanRequest("운영정책 위반");

        mockMvc.perform(post("/admin/api/members/{aliceId}/ban", aliceId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        // When & Then
        MvcResult result = mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.email == 'carol@test.com')].bannedAt").value(hasItem(nullValue())))
                .andReturn();

        List<String> bannedAts = JsonPath.read(result.getResponse().getContentAsString(), "$.items[?(@.email == 'alice@test.com')].bannedAt");
        Instant bannedAt = Instant.parse(bannedAts.getFirst());

        assertThat(Duration.between(bannedAt, Instant.now())).isLessThanOrEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("사유 없이 BANNED가 된 회원의 로그인 거절 응답에는 사유가 없다.")
    void bannedLoginRejectionWithoutReason() throws Exception {
        // Given
        UUID bobId = testFixtures.signup("bob@test.com");
        testFixtures.changeStatus(bobId, "BANNED");

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("bob@test.com", "pass1234"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_BANNED"))
                .andExpect(jsonPath("$.details.reason").doesNotExist());
    }
}
