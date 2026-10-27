package com.myroutine.settlement.web;

import com.myroutine.common.security.CurrentMember;
import com.myroutine.common.web.CursorPage;
import com.myroutine.settlement.application.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementQueryService settlementQueryService;
    private final SettlementService settlementService;

    @GetMapping("/api/shops/{shopId}/settlements")
    public ResponseEntity<CursorPage<SettlementResponse>> getSettlements(
            @CurrentMember UUID memberId,
            @PathVariable UUID shopId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "1 이상이어야 합니다.") @Max(value = 50, message = "50 이하여야 합니다.") int size
    ) {
        CursorPage<SettlementResult> result = settlementQueryService.getSettlements(memberId, shopId, cursor, size);
        return ResponseEntity.ok().body(new CursorPage<>(result.items().stream().map(SettlementResponse::from).toList(), result.nextCursor()));
    }

    @GetMapping("/api/shops/{shopId}/settlements/{id}")
    public ResponseEntity<SettlementDetailResponse> getSettlement(
            @CurrentMember UUID memberId,
            @PathVariable UUID shopId,
            @PathVariable UUID id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "1 이상이어야 합니다.") @Max(value = 50, message = "50 이하여야 합니다.") int size
    ) {
        SettlementDetailResult result = settlementQueryService.getSettlement(memberId, shopId, id, cursor, size);
        CursorPage<SettlementItemResponse> items = new CursorPage<>(result.items().items().stream().map(SettlementItemResponse::from).toList(), result.items().nextCursor());
        return ResponseEntity.ok().body(new SettlementDetailResponse(SettlementResponse.from(result.settlement()), items));
    }

    @PostMapping("/admin/api/settlements/run")
    public ResponseEntity<SettlementRunResult> runSettlement(
            @RequestParam YearMonth period
    ) {
        SettlementRunResult result = settlementService.run(period);
        return ResponseEntity.ok().body(result);
    }
}
