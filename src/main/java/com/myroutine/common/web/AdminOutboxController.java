package com.myroutine.common.web;

import com.myroutine.common.outbox.AdminOutboxResult;
import com.myroutine.common.outbox.AdminOutboxService;
import com.myroutine.common.outbox.OutboxStatus;
import com.myroutine.common.security.CurrentMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admin/api/outbox")
@RequiredArgsConstructor
public class AdminOutboxController {

    private final AdminOutboxService adminOutboxService;

    @GetMapping
    public ResponseEntity<CursorPage<AdminOutboxResponse>> getEvents(
            @RequestParam OutboxStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "1 이상이어야 합니다.") @Max(value = 50, message = "50 이하여야 합니다.") int size
    ) {
        CursorPage<AdminOutboxResult> result = adminOutboxService.getEvents(status, cursor, size);
        List<AdminOutboxResponse> items = result.items().stream()
                .map(AdminOutboxResponse::from)
                .toList();
        return ResponseEntity.ok(new CursorPage<>(items, result.nextCursor()));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Void> retry(@CurrentMember UUID memberId, @PathVariable UUID id) {
        log.info("admin action={} target={} by={}", "outbox-retry", id, memberId);
        adminOutboxService.retry(id);
        return ResponseEntity.noContent().build();
    }
}
