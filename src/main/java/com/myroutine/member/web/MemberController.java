package com.myroutine.member.web;

import com.myroutine.common.security.CurrentMember;
import com.myroutine.member.application.MemberQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberQueryService memberQueryService;

    @GetMapping("/me")
    public ResponseEntity<MeResponse> getMe(@CurrentMember UUID memberId) {
        return ResponseEntity.ok(MeResponse.from(memberQueryService.getMe(memberId)));
    }
}
