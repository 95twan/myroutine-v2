package com.myroutine.notification.web;

import com.myroutine.common.model.Ids;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.NotificationRow;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminNotificationTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AdminNotificationTest(MockMvc mockMvc, TestFixtures testFixtures, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("FAILED 알림 목록을 조회한다.")
    void listFailedNotifications() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID n = Ids.newId();
        insertNotification(n, Ids.newId(), "FAILED");
        insertNotification(Ids.newId(), Ids.newId(), "SENT");

        // When & Then
        mockMvc.perform(get("/admin/api/notifications")
                        .param("status", "FAILED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(n.toString()))
                .andExpect(jsonPath("$.items[0].type").value("ORDER_PAID"))
                .andExpect(jsonPath("$.items[0].recipient").value("buyer@test.com"))
                .andExpect(jsonPath("$.items[0].attempts").value(5))
                .andExpect(jsonPath("$.items[0].lastError").value("smtp down"))
                .andExpect(jsonPath("$.items[0].body").doesNotExist());
    }

    @Test
    @DisplayName("FAILED 알림을 재시도하면 PENDING으로 돌아간다.")
    void retryFailedNotification() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID n = Ids.newId();
        UUID memberId = Ids.newId();
        insertNotification(n, memberId, "FAILED");

        // When & Then
        mockMvc.perform(post("/admin/api/notifications/{id}/retry", n)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        NotificationRow row = testFixtures.notificationRowsOf(memberId).get(0);
        assertThat(row.status()).isEqualTo("PENDING");
        assertThat(row.attempts()).isEqualTo(0);
        assertThat(row.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 알림의 재시도를 한 번 더 요청하면 실패한다. (FAILED가 아님)")
    void retryNotificationTwiceFails() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID n = Ids.newId();
        UUID memberId = Ids.newId();
        insertNotification(n, memberId, "FAILED");
        mockMvc.perform(post("/admin/api/notifications/{id}/retry", n)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(post("/admin/api/notifications/{id}/retry", n)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        NotificationRow row = testFixtures.notificationRowsOf(memberId).get(0);
        assertThat(row.status()).isEqualTo("PENDING");
        assertThat(row.attempts()).isEqualTo(0);
        assertThat(row.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 알림의 재시도를 실패한다. (존재하지 않는 알림)")
    void retryUnknownNotificationFails() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        // When & Then
        mockMvc.perform(post("/admin/api/notifications/{id}/retry", Ids.newId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private void insertNotification(UUID id, UUID memberId, String status) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                        INSERT INTO notification.notification (id, member_id, type, channel, recipient, title, body, dedup_key, status, attempts, last_error, next_attempt_at, version, created_at, updated_at)
                        VALUES (?, ?, 'ORDER_PAID', 'EMAIL', 'buyer@test.com', 't', 'b', ?, ?, 5, 'smtp down', ?, 0, ?, ?)
                        """,
                id, memberId, "dedup-" + id, status, Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)), now, now);
    }
}
