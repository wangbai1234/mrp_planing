package com.mrp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

class MigrationAcceptanceTest {

    // Read-only baseline from the already-applied main schema history on 2026-11-04.
    private static final Map<String, Integer> APPLIED_PRE_PHASE2_CHECKSUMS = Map.ofEntries(
            Map.entry("001", 41330765), Map.entry("002", -1942433625),
            Map.entry("003", -1714490985), Map.entry("004", -445695233),
            Map.entry("005", -1767010977), Map.entry("006", -2111147807),
            Map.entry("007", 1807392265), Map.entry("008", 1619926124),
            Map.entry("009", -1354973408), Map.entry("010", 1683886259),
            Map.entry("011", 698320898), Map.entry("012", 1212691787),
            Map.entry("013", 235423546), Map.entry("014", -422700656),
            Map.entry("015", 1055646511), Map.entry("016", -1835090679),
            Map.entry("017", 691765941), Map.entry("018", -64431433),
            Map.entry("019", 1995166092), Map.entry("020", 246700849));

    @BeforeAll
    static void configureDockerApi() {
        System.setProperty("api.version", System.getProperty("api.version", "1.40"));
    }

    @Test
    void emptyDatabaseMigratesToLatestAndSecondMigrationIsIdempotent() throws Exception {
        try (MySQLContainer<?> mysql = newDatabase()) {
            mysql.start();
            Flyway flyway = flyway(mysql, null);

            assertEquals(65, flyway.migrate().migrationsExecuted);
            flyway.validate();
            assertEquals(65, successfulMigrationCount(mysql));
            assertEquals(0, flyway.migrate().migrationsExecuted);
            assertEquals(65, successfulMigrationCount(mysql));
        }
    }

    @Test
    void version20UpgradePreservesChecksumsAndBusinessRows() throws Exception {
        try (MySQLContainer<?> mysql = newDatabase()) {
            mysql.start();
            Flyway version20 = flyway(mysql, MigrationVersion.fromVersion("20"));
            assertEquals(20, version20.migrate().migrationsExecuted);
            version20.validate();

            try (Connection connection = connection(mysql);
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO forecast_version "
                        + "(version_no, file_name, status) VALUES "
                        + "(987654, 'migration-acceptance-sentinel', 'IMPORTED')");
            }
            Map<String, Integer> originalChecksums = first20Checksums(mysql);
            assertEquals(20, originalChecksums.size());
            assertEquals(APPLIED_PRE_PHASE2_CHECKSUMS, originalChecksums);

            Flyway latest = flyway(mysql, null);
            assertEquals(45, latest.migrate().migrationsExecuted);
            latest.validate();
            assertEquals(originalChecksums, first20Checksums(mysql));
            assertEquals(1, scalar(mysql, "SELECT COUNT(*) FROM forecast_version "
                    + "WHERE version_no = 987654 AND file_name = 'migration-acceptance-sentinel'"));
            assertEquals(65, successfulMigrationCount(mysql));
            assertEquals(0, latest.migrate().migrationsExecuted);
            assertTrue(latest.info().current().getVersion()
                    .compareTo(MigrationVersion.fromVersion("65")) == 0);
        }
    }

    private static MySQLContainer<?> newDatabase() {
        return new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("mrp_migration_acceptance")
                .withUsername("acceptance")
                .withPassword("acceptance")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
    }

    private static Flyway flyway(MySQLContainer<?> mysql, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static Connection connection(MySQLContainer<?> mysql) throws Exception {
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    private static int successfulMigrationCount(MySQLContainer<?> mysql) throws Exception {
        return scalar(mysql, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1");
    }

    private static int scalar(MySQLContainer<?> mysql, String sql) throws Exception {
        try (Connection connection = connection(mysql);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static Map<String, Integer> first20Checksums(MySQLContainer<?> mysql) throws Exception {
        Map<String, Integer> checksums = new LinkedHashMap<>();
        try (Connection connection = connection(mysql);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT version, checksum FROM flyway_schema_history "
                        + "WHERE success = 1 AND CAST(version AS UNSIGNED) <= 20 ORDER BY installed_rank")) {
            while (rows.next()) {
                checksums.put(rows.getString(1), rows.getInt(2));
            }
        }
        return checksums;
    }
}
