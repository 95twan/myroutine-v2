package com.myroutine.support;

import com.myroutine.member.application.*;
import com.myroutine.shop.application.OpenShopCommand;
import com.myroutine.shop.application.OpenShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TestFixtures {

    private final SignupService signupService;
    private final OpenShopService openShopService;
    private final LoginService loginService;
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

    public UUID openShop(UUID memberId, String businessNumber) {
        OpenShopCommand command = new OpenShopCommand(
                "shop",
                businessNumber,
                "shop@test.com",
                "010-1111-1111",
                "shop address"
        );
        return openShopService.open(memberId, command);
    }

    public void closeShop(UUID shopId) {
        jdbcTemplate.update("UPDATE shop.shop SET status = 'CLOSED' WHERE id = ?", shopId);
    }

    public String login(String email) {
        return loginService.login(new LoginCommand(email, "pass1234")).accessToken();
    }
}
