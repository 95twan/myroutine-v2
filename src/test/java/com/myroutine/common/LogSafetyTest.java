package com.myroutine.common;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.order.application.CheckoutResult;
import com.myroutine.support.FakeMailSender;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.SentMail;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
@AutoConfigureMockMvc
class LogSafetyTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final FakeMailSender fakeMailSender;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public LogSafetyTest(MockMvc mockMvc, TestFixtures testFixtures, FakeMailSender fakeMailSender, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.fakeMailSender = fakeMailSender;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("가입·로그인·갱신·결제 승인 뒤에도 비밀번호·인증코드·토큰·paymentKey가 로그에 남지 않는다.")
    void secretsAreNotLogged(CapturedOutput output) throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 10000, 10);

        String email = "logsafe@test.com";
        String password = "LogSafe9183x";

        // When
        postJson("/api/auth/signup/email-verifications", Map.of("email", email), 202);
        SentMail mail = fakeMailSender.sent().stream().filter(m -> m.to().equals(email)).reduce((first, second) -> second).orElseThrow();
        String code = mail.body().split("인증코드: ")[1].substring(0, 6);

        String confirmBody = postJson("/api/auth/signup/email-verifications/confirm", Map.of("email", email, "code", code), 200);
        String verificationToken = JsonPath.read(confirmBody, "$.verificationToken");

        String signupBody = postJson("/api/auth/signup", Map.of(
                "email", email, "password", password, "nickname", "logsafe", "name", "테스터", "verificationToken", verificationToken), 201);
        String signupAccess = JsonPath.read(signupBody, "$.accessToken");
        String signupRefresh = JsonPath.read(signupBody, "$.refreshToken");
        UUID buyerId = UUID.fromString(JsonPath.read(signupBody, "$.memberId"));

        String loginBody = postJson("/api/auth/login", Map.of("email", email, "password", password), 200);
        String loginAccess = JsonPath.read(loginBody, "$.accessToken");
        String loginRefresh = JsonPath.read(loginBody, "$.refreshToken");

        String refreshBody = postJson("/api/auth/refresh", Map.of("refreshToken", loginRefresh), 200);
        String refreshAccess = JsonPath.read(refreshBody, "$.accessToken");
        String refreshRefresh = JsonPath.read(refreshBody, "$.refreshToken");

        CheckoutResult checkout = testFixtures.createPendingOrder(buyerId, productId);
        UUID orderId = checkout.orderId();
        String paymentKey = "pk_logsafe_" + orderId;
        mockMvc.perform(post("/api/orders/{orderId}/payment/confirm", orderId)
                        .header("Authorization", "Bearer " + refreshAccess)
                        .header("Idempotency-Key", orderId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "paymentKey", paymentKey, "pgOrderId", checkout.pgOrderId(), "amount", checkout.totalAmount()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        UUID paymentId = jdbcTemplate.queryForObject("SELECT id FROM payment.payment WHERE order_id = ?", UUID.class, orderId);

        // Then
        // FakeMailSender는 LogMailSender처럼 메일 본문(인증코드)을 찍는 테스트용 구현이라 그 줄은 뺀다. 운영의 SmtpMailSender는 본문을 로그에 남기지 않는다.
        String log = output.getAll().lines()
                .filter(line -> !line.contains("FakeMailSender"))
                .collect(Collectors.joining("\n"));

        assertThat(log).isNotEmpty();
        assertThat(log).contains("pg confirm paymentId=" + paymentId + " result=Approved");

        // 무작위 6자리가 UUID·시각의 숫자와 우연히 겹쳐 실패하지 않도록 앞뒤가 영숫자가 아닌 경우만 같은 값으로 본다.
        assertThat(Pattern.compile("(?<![0-9A-Za-z])" + code + "(?![0-9A-Za-z])").matcher(log).find()).as("인증코드").isFalse();
        assertThat(log).as("비밀번호").doesNotContain(password);
        assertThat(log).as("인증 토큰").doesNotContain(verificationToken);
        assertThat(log).as("가입 access 토큰").doesNotContain(signupAccess);
        assertThat(log).as("가입 refresh 토큰").doesNotContain(signupRefresh);
        assertThat(log).as("로그인 access 토큰").doesNotContain(loginAccess);
        assertThat(log).as("로그인 refresh 토큰").doesNotContain(loginRefresh);
        assertThat(log).as("갱신 access 토큰").doesNotContain(refreshAccess);
        assertThat(log).as("갱신 refresh 토큰").doesNotContain(refreshRefresh);
        assertThat(log).as("paymentKey").doesNotContain(paymentKey);
    }

    private String postJson(String path, Map<String, ?> body, int expectedStatus) throws Exception {
        return mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
