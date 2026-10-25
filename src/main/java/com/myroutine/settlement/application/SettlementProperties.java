package com.myroutine.settlement.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("myroutine.settlement")
public record SettlementProperties(
        @Min(0) @Max(10000) int feeRateBp
) {
}
