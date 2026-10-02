package com.myroutine.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

@ActiveProfiles("test")
@SpringBootTest
public abstract class IntegrationTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    static {   // 싱글턴: JVM 전체에서 한 번만 띄워 모든 테스트 클래스가 공유
        postgres.start();
    }

    @AfterEach
    void truncateAllTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT schemaname || '.' || tablename FROM pg_tables "
                        + "WHERE schemaname NOT IN ('pg_catalog', 'information_schema') "
                        + "AND tablename <> 'flyway_schema_history'",
                String.class);
        if (tables.isEmpty()) {
            return;
        }
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
    }
}
