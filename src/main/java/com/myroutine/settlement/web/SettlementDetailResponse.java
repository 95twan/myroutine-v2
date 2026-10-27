package com.myroutine.settlement.web;

import com.myroutine.common.web.CursorPage;

public record SettlementDetailResponse(
        SettlementResponse settlement,
        CursorPage<SettlementItemResponse> items
) {
}
