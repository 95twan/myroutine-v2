package com.myroutine.member.web;

import com.myroutine.common.security.CurrentMember;
import com.myroutine.member.application.MemberProfileService;
import com.myroutine.member.application.MemberQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberQueryService memberQueryService;
    private final MemberProfileService memberProfileService;

    @GetMapping("/me")
    public ResponseEntity<MeResponse> getMe(@CurrentMember UUID memberId) {
        return ResponseEntity.ok(MeResponse.from(memberQueryService.getMe(memberId)));
    }

    @PatchMapping("/me")
    public ResponseEntity<MeResponse> changeProfile(@CurrentMember UUID memberId, @Valid @RequestBody ChangeProfileRequest request) {
        return ResponseEntity.ok(MeResponse.from(memberProfileService.changeProfile(memberId, request.toCommand())));
    }
}
