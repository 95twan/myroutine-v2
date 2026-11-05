package com.myroutine.notification.web;

import com.myroutine.common.security.CurrentMember;
import com.myroutine.common.web.CursorPage;
import com.myroutine.notification.application.AdminNotificationResult;
import com.myroutine.notification.application.NotificationService;
import com.myroutine.notification.domain.NotificationStatus;
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
@RequestMapping("/admin/api/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<CursorPage<AdminNotificationResponse>> getForAdmin(
            @RequestParam NotificationStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "1 이상이어야 합니다.") @Max(value = 50, message = "50 이하여야 합니다.") int size
    ) {
        CursorPage<AdminNotificationResult> result = notificationService.getForAdmin(status, cursor, size);
        List<AdminNotificationResponse> items = result.items().stream()
                .map(AdminNotificationResponse::from)
                .toList();
        return ResponseEntity.ok(new CursorPage<>(items, result.nextCursor()));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Void> retry(@CurrentMember UUID memberId, @PathVariable UUID id) {
        log.info("admin action={} target={} by={}", "notification-retry", id, memberId);
        notificationService.retry(id);
        return ResponseEntity.noContent().build();
    }
}
