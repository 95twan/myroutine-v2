package com.myroutine.member.web;

import com.myroutine.common.security.CurrentMember;
import com.myroutine.common.web.CursorPage;
import com.myroutine.member.application.AdminInquiryResult;
import com.myroutine.member.application.InquiryService;
import com.myroutine.member.domain.InquiryStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/api/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {
    private final InquiryService inquiryService;

    @GetMapping
    public ResponseEntity<CursorPage<AdminInquiryResponse>> getInquiries(
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "1 이상이어야 합니다.") @Max(value = 50, message = "50 이하여야 합니다.") int size
    ) {
        CursorPage<AdminInquiryResult> results = inquiryService.getForAdmin(status, cursor, size);
        List<AdminInquiryResponse> items = results.items().stream()
                .map(AdminInquiryResponse::from)
                .toList();
        return ResponseEntity.ok(new CursorPage<>(items, results.nextCursor()));
    }

    @PostMapping("/{id}/answer")
    public ResponseEntity<Void> answerInquiry(
            @CurrentMember UUID adminId,
            @PathVariable("id") UUID inquiryId,
            @Valid @RequestBody AnswerInquiryRequest request
    ) {
        inquiryService.answer(adminId, inquiryId, request.content());
        return ResponseEntity.noContent().build();
    }
}
