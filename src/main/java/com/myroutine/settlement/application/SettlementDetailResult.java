package com.myroutine.settlement.application;

import com.myroutine.common.web.CursorPage;

public record SettlementDetailResult(
        SettlementResult settlement,
        CursorPage<SettlementItemResult> items
) {
}
