package com.myroutine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

public class ModularityTest {

    @Test
    @DisplayName("모듈 간 의존 규칙을 위반하지 않는다.")
    void verifyModules() {
        // Given

        // When & Then
        ApplicationModules.of(MyRoutineApplication.class).verify();
    }
}
