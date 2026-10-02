package com.myroutine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CiFailureProbeTest {

    @Test
    void alwaysFails() {
        assertThat(1).isEqualTo(2);
    }
}
