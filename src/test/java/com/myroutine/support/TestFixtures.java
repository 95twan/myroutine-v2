package com.myroutine.support;

import com.myroutine.member.application.SignupCommand;
import com.myroutine.member.application.SignupResult;
import com.myroutine.member.application.SignupService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TestFixtures {

    private final SignupService signupService;
    private final JdbcTemplate jdbcTemplate;

    public UUID signup(String email) {

        SignupCommand command = new SignupCommand(
                email,
                "pass1234",
                email.substring(0, email.indexOf("@")),
                "테스터"
        );

        SignupResult result = signupService.signUp(command);
        return result.memberId();
    }

    public String token(String email) {
        SignupCommand command = new SignupCommand(
                email,
                "pass1234",
                email.substring(0, email.indexOf("@")),
                "테스터"
        );

        SignupResult result = signupService.signUp(command);
        return result.token().accessToken();
    }

    public void changeStatus(UUID memberId, String status) {
        jdbcTemplate.update("UPDATE member.member SET status = ? WHERE id = ?", status, memberId);
    }
}
