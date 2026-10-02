package com.myroutine;

import com.myroutine.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class SchemaTest extends IntegrationTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void commonSchemaExists() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'common'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
