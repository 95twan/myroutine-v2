package com.myroutine.common.web;

import com.myroutine.common.dlt.AdminDltService;
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
@RequestMapping("/admin/api/dlt")
@RequiredArgsConstructor
public class AdminDltController {

    private final AdminDltService adminDltService;

    @GetMapping
    public ResponseEntity<List<DltTopicResponse>> getTopics() {
        List<DltTopicResponse> topics = adminDltService.getTopics().stream()
                .map(DltTopicResponse::from)
                .toList();
        return ResponseEntity.ok(topics);
    }

    @GetMapping("/{topic}")
    public ResponseEntity<List<DltRecordResponse>> getRecords(
            @PathVariable String topic,
            @RequestParam(defaultValue = "50") @Min(value = 1, message = "1 이상이어야 합니다.") @Max(value = 100, message = "100 이하여야 합니다.") int limit
    ) {
        List<DltRecordResponse> records = adminDltService.getRecords(topic, limit).stream()
                .map(DltRecordResponse::from)
                .toList();
        return ResponseEntity.ok(records);
    }

    @PostMapping("/{topic}/{partition}/{offset}/replay")
    public ResponseEntity<Void> replay(
            @CurrentMember UUID memberId,
            @PathVariable String topic,
            @PathVariable int partition,
            @PathVariable long offset
    ) {
        log.info("admin action={} target={}/{}/{} by={}", "dlt-replay", topic, partition, offset, memberId);
        adminDltService.replay(topic, partition, offset);
        return ResponseEntity.noContent().build();
    }
}
