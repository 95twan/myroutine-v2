package com.myroutine.member.web;

import com.myroutine.common.security.CurrentMember;
import com.myroutine.common.web.CursorPage;
import com.myroutine.member.application.InquiryDetailResult;
import com.myroutine.member.application.InquiryResult;
import com.myroutine.member.application.InquiryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class InquiryController {
    private final InquiryService inquiryService;

    @PostMapping
    public ResponseEntity<InquiryIdResponse> writeInquiry(
            @CurrentMember UUID memberId,
            @Valid @RequestBody WriteInquiryRequest request
    ) {
        UUID inquiryId = inquiryService.write(memberId, request.title(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(new InquiryIdResponse(inquiryId));
    }

    @GetMapping
    public ResponseEntity<CursorPage<InquiryResponse>> getMyInquiries(
            @CurrentMember UUID memberId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "1 이상이어야 합니다.") @Max(value = 50, message = "50 이하여야 합니다.") int size
    ) {
        CursorPage<InquiryResult> results = inquiryService.getMyInquiries(memberId, cursor, size);
        List<InquiryResponse> items = results.items().stream()
                .map(InquiryResponse::from)
                .toList();
        return ResponseEntity.ok(new CursorPage<>(items, results.nextCursor()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InquiryDetailResponse> getMyInquiry(
            @CurrentMember UUID memberId,
            @PathVariable("id") UUID inquiryId
    ) {
        InquiryDetailResult result = inquiryService.getMyInquiry(memberId, inquiryId);
        return ResponseEntity.ok(InquiryDetailResponse.from(result));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<InquiryDetailResponse> editInquiry(
            @CurrentMember UUID memberId,
            @PathVariable("id") UUID inquiryId,
            @Valid @RequestBody EditInquiryRequest request
    ) {
        InquiryDetailResult result = inquiryService.edit(memberId, inquiryId, request.toCommand());
        return ResponseEntity.ok(InquiryDetailResponse.from(result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInquiry(
            @CurrentMember UUID memberId,
            @PathVariable("id") UUID inquiryId
    ) {
        inquiryService.delete(memberId, inquiryId);
        return ResponseEntity.noContent().build();
    }
}
