package com.myroutine.member.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.common.model.Ids;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

class RefreshTokenTest {

    @Test
    @DisplayName("만든 refresh 문자열을 해석하면 같은 sessionId와 secret으로 돌아온다.")
    void generateAndParseRoundTrip() {
        // Given
        UUID sessionId = Ids.newId();

        // When
        RefreshToken refreshToken = RefreshToken.generate(sessionId);
        RefreshToken parsed = RefreshToken.parse(refreshToken.value());

        // Then
        assertThat(refreshToken.value()).isEqualTo(sessionId + "." + refreshToken.secret());
        assertThat(refreshToken.secret()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(parsed.sessionId()).isEqualTo(sessionId);
        assertThat(parsed.secret()).isEqualTo(refreshToken.secret());
        assertThat(parsed.secretHash()).isEqualTo(refreshToken.secretHash());
        assertThat(refreshToken.secretHash()).isNotEqualTo(refreshToken.secret());

        RefreshToken refreshToken2 = RefreshToken.generate(sessionId);

        assertThat(refreshToken2.secret()).isNotEqualTo(refreshToken.secret());
    }

    @ParameterizedTest
    @MethodSource("invalidRefreshTokens")
    @DisplayName("refresh 문자열 해석을 실패한다. (잘못된 형식, UNAUTHORIZED)")
    void parseInvalidFormatFails(String invalid) {
        // Given


        // When & Then
        assertThatThrownBy(() -> RefreshToken.parse(invalid))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("toString에는 secret이 들어 있지 않다.")
    void toStringHidesSecret() {
        // Given
        RefreshToken refreshToken = RefreshToken.generate(Ids.newId());

        // When
        String result = refreshToken.toString();

        // Then
        assertThat(result).doesNotContain(refreshToken.secret()).contains(refreshToken.sessionId().toString());
        assertThat(result).doesNotContain(refreshToken.secret());
    }

    public static Stream<String> invalidRefreshTokens() {
        return Stream.of(null, "", "abc", "a.b.c", "not-a-uuid.secret");
    }
}
