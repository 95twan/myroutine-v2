package com.myroutine.common.web;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorTest {

    @Test
    @DisplayName("커서를 인코딩한 뒤 디코딩하면 같은 값이 나온다.")
    void encodeAndDecode() {
        // Given
        Instant now = Instant.now();
        UUID uuid = UUID.randomUUID();
        Cursor cursor = new Cursor(now, uuid);

        // When & Then
        String encode = cursor.encode();
        Cursor decodedCursor = Cursor.decode(encode);

        assertThat(decodedCursor.createdAt()).isEqualTo(now);
        assertThat(decodedCursor.id()).isEqualTo(uuid);
    }

    @ParameterizedTest()
    @DisplayName("커서 디코딩을 실패한다. (잘못된 형식)")
    @MethodSource("decodeWithInvalidValueProvider")
    void decodeWithInvalidValue(String cursor) {
        // Given

        // When & Then
        assertThatThrownBy(() -> Cursor.decode(cursor))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
    }

    public static Stream<Arguments> decodeWithInvalidValueProvider() {
        return Stream.of(
                Arguments.of("not-base64!!"),
                Arguments.of("MjAyNi0xMC0wN1QxMjozNDo1Ni4xMjM0NTZa"),
                Arguments.of("MjAyNi0xMC0wN1QxMjozNDo1Ni4xMjM0NTZafDU1MGU4NDAwLWUyOWItNDFkNC1hNzE2LTQ0NjY1NTQ0MDAwMHxleHRyYQ"),
                Arguments.of("bm90LWEtdGltZXw1NTBlODQwMC1lMjliLTQxZDQtYTcxNi00NDY2NTU0NDAwMDA"),
                Arguments.of("MjAyNi0xMC0wN1QxMjozNDo1Ni4xMjM0NTZafG5vdC1hLXV1aWQ"),
                Arguments.of("fDU1MGU4NDAwLWUyOWItNDFkNC1hNzE2LTQ0NjY1NTQ0MDAwMA"),
                Arguments.of("")
        );
    }
}
