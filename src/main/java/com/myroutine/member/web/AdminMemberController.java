package com.myroutine.member.web;

import com.myroutine.common.web.CursorPage;
import com.myroutine.member.application.AdminMemberResult;
import com.myroutine.member.application.AdminMemberService;
import com.myroutine.member.domain.MemberStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/api/members")
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @GetMapping
    public ResponseEntity<CursorPage<AdminMemberResponse>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) MemberStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "1 이상이어야 합니다.") @Max(value = 50, message = "50 이하여야 합니다.") int size
    ) {
        CursorPage<AdminMemberResult> result = adminMemberService.search(query, status, cursor, size);
        return ResponseEntity.ok(new CursorPage<>(result.items().stream().map(AdminMemberResponse::from).toList(), result.nextCursor()));
    }

    @PostMapping("/{id}/ban")
    public ResponseEntity<Void> ban(@PathVariable UUID id, @Valid @RequestBody BanRequest request) {
        adminMemberService.ban(id, request.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unban")
    public ResponseEntity<Void> unban(@PathVariable UUID id) {
        adminMemberService.unban(id);
        return ResponseEntity.noContent().build();
    }
}
