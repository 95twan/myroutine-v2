package com.myroutine.member.web;

import com.myroutine.common.security.AuthClaims;
import com.myroutine.common.security.CurrentMember;
import com.myroutine.member.application.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberQueryService memberQueryService;
    private final MemberProfileService memberProfileService;
    private final WithdrawService withdrawService;
    private final PasswordService passwordService;

    @GetMapping("/me")
    public ResponseEntity<MeResponse> getMe(@CurrentMember UUID memberId) {
        return ResponseEntity.ok(MeResponse.from(memberQueryService.getMe(memberId)));
    }

    @PatchMapping("/me")
    public ResponseEntity<MeResponse> changeProfile(@CurrentMember UUID memberId, @Valid @RequestBody ChangeProfileRequest request) {
        return ResponseEntity.ok(MeResponse.from(memberProfileService.changeProfile(memberId, request.toCommand())));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@CurrentMember UUID memberId) {
        withdrawService.withdraw(memberId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/password")
    public ResponseEntity<TokenResponse> changePassword(@AuthenticationPrincipal AuthClaims claims, @Valid @RequestBody ChangePasswordRequest request) {
        TokenResult result = passwordService.change(claims.memberId(), claims.sessionId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(TokenResponse.from(result));
    }
}
