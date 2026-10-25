package com.myroutine.settlement.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("myroutine.settlement")
@Validated
public record SettlementProperties(
        @Min(0) @Max(10000) int feeRateBp
) {
}
