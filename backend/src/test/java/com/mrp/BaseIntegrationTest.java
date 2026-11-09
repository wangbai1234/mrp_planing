package com.mrp;

import com.mrp.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
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
// Each subclass restarts MySQL; cached connections must not outlive that container.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseIntegrationTest {
    public static final String TEST_ADMIN_PASSWORD = "integration-only-admin123";

    @Autowired @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate testJdbc;

    @BeforeEach
    void allowTestAdminRequests() {
        testJdbc.update("UPDATE user SET need_change_password = 0 WHERE username = ?", "admin");
    }

    static {
        // Docker Desktop 4.73 exposes API >= 1.40. Testcontainers 1.20.4
        // otherwise probes /v1.24/info and Docker returns HTTP 400.
        System.setProperty("api.version", System.getProperty("api.version", "1.40"));
    }

    @Container
    protected static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mrp_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("MRP_BOOTSTRAP_ADMIN_PASSWORD", () -> TEST_ADMIN_PASSWORD);
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
        String redisPort = System.getenv().getOrDefault("MRP_REDIS_PORT", "6379");
        registry.add("spring.data.redis.port", () -> redisPort);
        registry.add("redisson.single-server-config.address", () -> "redis://127.0.0.1:" + redisPort);
        registry.add("mybatis.mapper-locations", () -> "classpath:mapper/**/*.xml");
        registry.add("mybatis.configuration.map-underscore-to-camel-case", () -> "true");
        registry.add("mrp.jwt.secret", () -> "test-secret-key-must-be-at-least-256-bits-long-for-hs256");
        registry.add("mrp.jwt.expiration-seconds", () -> "3600");
        registry.add("mrp.file.upload-dir", () -> "/tmp/mrp-test-uploads");
        registry.add("feishu.app-id", () -> "test-app-id");
        registry.add("feishu.app-secret", () -> "test-app-secret");
        registry.add("feishu.redirect-uri", () -> "http://localhost:5180/login/callback/");
    }
}
