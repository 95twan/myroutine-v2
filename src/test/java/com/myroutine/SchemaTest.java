package com.myroutine;

import com.myroutine.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class SchemaTest extends IntegrationTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("common 스키마가 존재한다.")
    void commonSchemaExists() {
        // Given

        // When
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'common'",
                Integer.class);

        // Then
        assertThat(count).isEqualTo(1);
    }
}
