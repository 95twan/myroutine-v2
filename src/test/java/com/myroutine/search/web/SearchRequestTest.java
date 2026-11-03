package com.myroutine.search.web;

import com.myroutine.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SearchRequestTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;

    @Autowired
    public SearchRequestTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @ParameterizedTest
    @MethodSource("invalidSearchParamsProvider")
    @DisplayName("q 길이나 size가 범위를 벗어나 검색을 실패한다. (INVALID_REQUEST)")
    void searchWithInvalidParams(String q, String size) throws Exception {
        // Given
        MockHttpServletRequestBuilder request = get("/api/search/products")
                .param("q", q);
        if (size != null) {
            request.param("size", size);
        }

        // When & Then
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    }

    static Stream<Arguments> invalidSearchParamsProvider() {
        return Stream.of(
                Arguments.of("", null),
                Arguments.of("가".repeat(51), null),
                Arguments.of("테스트", "0"),
                Arguments.of("테스트", "51")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-cursor!!", "YWJj"})
    @DisplayName("해석할 수 없는 커서로 검색을 실패한다. (INVALID_REQUEST)")
    void searchWithInvalidCursor(String cursor) throws Exception {
        // Given

        // When & Then
        mockMvc.perform(get("/api/search/products")
                        .param("q", "테스트")
                        .param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("경계값(q 50자, size 50)으로 검색한다.")
    void searchAtMaxBoundary() throws Exception {
        // Given

        // When & Then
        mockMvc.perform(get("/api/search/products")
                        .param("q", "가".repeat(50))
                        .param("size", "50"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("invalidAutocompleteQueryProvider")
    @DisplayName("q 길이가 범위를 벗어나 자동완성을 실패한다. (INVALID_REQUEST)")
    void autocompleteWithInvalidLength(String q) throws Exception {
        // Given

        // When & Then
        mockMvc.perform(get("/api/search/autocomplete")
                        .param("q", q))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    static Stream<String> invalidAutocompleteQueryProvider() {
        return Stream.of("루", "가".repeat(21));
    }

    @Test
    @DisplayName("경계값(q 2자)으로 자동완성한다.")
    void autocompleteAtMinBoundary() throws Exception {
        // Given

        // When & Then
        mockMvc.perform(get("/api/search/autocomplete")
                        .param("q", "루틴"))
                .andExpect(status().isOk());
    }
}
