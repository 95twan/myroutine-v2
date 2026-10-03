package com.myroutine.support;

import com.myroutine.member.domain.MemberRepository;
import com.myroutine.member.web.SignupRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TableCleanupTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;


    @Autowired
    public TableCleanupTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }


    @Test
    @Order(1)
    @DisplayName("회원 가입을 한다.")
    void createMember() throws Exception {
        // Given
        SignupRequest request = new SignupRequest("test@test.com", "password123", "nick", "name");

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(2)
    @DisplayName("테이블이 초기화된다.")
    void tableIsEmptyAfterCleanup() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM member.member", Long.class);
        assertThat(count).isEqualTo(0);
    }
}
