package com.myroutine.member.web;

import com.myroutine.common.security.AuthClaims;
import com.myroutine.member.application.LoginService;
import com.myroutine.member.application.LogoutService;
import com.myroutine.member.application.SignupService;
import com.myroutine.member.application.TokenRefreshService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final SignupService signupService;
    private final LoginService loginService;
    private final TokenRefreshService tokenRefreshService;
    private final LogoutService logoutService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signUp(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = SignupResponse.from(signupService.signUp(request.toCommand()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(TokenResponse.from(loginService.login(request.toCommand())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(TokenResponse.from(tokenRefreshService.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthClaims claims, @RequestParam(defaultValue = "false") boolean all) {
        logoutService.logout(claims.memberId(), claims.sessionId(), all);
        return ResponseEntity.noContent().build();
    }
}
