package com.myroutine.settlement.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SettlementProperties.class)
public class SettlementConfig {
}
