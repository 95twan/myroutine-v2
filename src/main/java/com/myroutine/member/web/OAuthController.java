package com.myroutine.member.web;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.member.application.OAuthLoginResult;
import com.myroutine.member.application.OAuthLoginService;
import com.myroutine.member.application.TokenResult;
import com.myroutine.member.domain.OAuthProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthLoginService oAuthLoginService;

    @PostMapping("/{provider}/login")
    public ResponseEntity<OAuthLoginResponse> oauthLogin(@PathVariable String provider, @Valid @RequestBody OAuthLoginRequest request) {
        OAuthProvider p;
        try {
            p = OAuthProvider.valueOf(provider.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        OAuthLoginResult result = oAuthLoginService.login(p, request.code(), request.state());
        return ResponseEntity.ok(OAuthLoginResponse.from(result));
    }

    @PostMapping("/signup")
    public ResponseEntity<TokenResponse> oauthSignup(@Valid @RequestBody SocialSignupRequest request) {
        TokenResult result = oAuthLoginService.signup(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(TokenResponse.from(result));
    }
}
