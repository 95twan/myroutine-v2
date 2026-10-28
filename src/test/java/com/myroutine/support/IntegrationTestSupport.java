package com.myroutine.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

@ActiveProfiles("test")
@SpringBootTest
public abstract class IntegrationTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakePayoutGateway payoutGateway;

    @Autowired
    FakeMailSender mailSender;

    @Autowired
    StringRedisTemplate redisTemplate;

    protected static final FakePgServer pg = FakePgServer.start();

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @ServiceConnection
    protected static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.2");

    @ServiceConnection(name = "redis")
    protected static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4.11").withExposedPorts(6379);

    static final MinIOContainer minio = new MinIOContainer(DockerImageName.parse("chainguard/minio:latest").asCompatibleSubstituteFor("minio/minio"));

    static { // 싱글턴: JVM 전체에서 한 번만 띄워 모든 테스트 클래스가 공유
        Startables.deepStart(postgres, minio, kafka, redis).join();
    }

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("myroutine.storage.endpoint", minio::getS3URL);
        registry.add("myroutine.storage.access-key", minio::getUserName);
        registry.add("myroutine.storage.secret-key", minio::getPassword);
        registry.add("myroutine.storage.public-base-url", () -> minio.getS3URL() + "/myroutine");
        registry.add("myroutine.toss.base-url", pg::baseUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
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

    @AfterEach
    void resetPg() {
        pg.reset();
    }

    @AfterEach
    void resetFakePayoutGateway() {
        payoutGateway.reset();
    }

    @AfterEach
    void resetFakeMailSender() {
        mailSender.reset();
    }

    @AfterEach
    void resetRedis() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }
}
