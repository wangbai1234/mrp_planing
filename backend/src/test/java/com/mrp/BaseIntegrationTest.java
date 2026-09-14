package com.mrp;

import com.mrp.config.SecurityConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests that need a real MySQL database.
 * Uses Testcontainers to spin up a MySQL8.x container.
 * Flyway handles all migrations automatically.
 */
@SpringBootTest(classes = {MrpApiApplication.class, SecurityConfig.class})
@ActiveProfiles("integration")
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    protected static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mrp_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.url", mysql::getJdbcUrl);
        registry.add("spring.flyway.user", mysql::getUsername);
        registry.add("spring.flyway.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("spring.batch.jdbc.initialize-schema", () -> "always");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");
        registry.add("redisson.single-server-config.address", () -> "redis://127.0.0.1:6379");
        registry.add("mybatis.mapper-locations", () -> "classpath:mapper/**/*.xml");
        registry.add("mybatis.configuration.map-underscore-to-camel-case", () -> "true");
        registry.add("mrp.jwt.secret", () -> "test-secret-key-must-be-at-least-256-bits-long-for-hs256");
        registry.add("mrp.jwt.expiration-seconds", () -> "3600");
        registry.add("mrp.file.upload-dir", () -> "/tmp/mrp-test-uploads");
    }
}
