package com.myroutine.search.infrastructure;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Component
public class ProductIndexDefinition {
    private final String json;

    public ProductIndexDefinition() {
        try {
            this.json = new ClassPathResource("search/products-index.json")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String json() {
        return json;
    }
}
