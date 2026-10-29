package com.myroutine.member.web;

import com.myroutine.common.security.AuthClaims;
import com.myroutine.member.application.*;
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
    private final SignupVerificationService signupVerificationService;
    private final PasswordService passwordService;

    @PostMapping("/signup/email-verifications")
    public ResponseEntity<Void> sendEmailVerification(@Valid @RequestBody SignupEmailRequest request) {
        signupVerificationService.send(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/signup/email-verifications/confirm")
    public ResponseEntity<SignupVerificationResponse> sendEmailVerification(@Valid @RequestBody SignupEmailConfirmRequest request) {
        String verificationToken = signupVerificationService.confirm(request.email(), request.code());
        return ResponseEntity.ok(new SignupVerificationResponse(verificationToken));
    }

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

    @PostMapping("/password-reset")
    public ResponseEntity<Void> passwordResetRequest(@Valid @RequestBody PasswordResetRequest request) {
        passwordService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> passwordResetConfirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordService.confirmReset(request.email(), request.code(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

}
