package com.mrp.auth.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class LegacyAdminBootstrap implements ApplicationRunner {

    // V001 is already applied; only this exact seeded hash may be replaced automatically.
    private static final String SEEDED_HASH = "$2b$10$F17Gmp5YjVbFVtsop3Eb6OITyzwSqepFInIulR6ZOFXEX69OeYc22";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapPassword;

    public LegacyAdminBootstrap(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate,
                                PasswordEncoder passwordEncoder,
                                @Value("${MRP_BOOTSTRAP_ADMIN_PASSWORD:}") String bootstrapPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapPassword = bootstrapPassword;
    }

    public static boolean isSeededAdmin(String username, String hash) {
        return "admin".equals(username) && SEEDED_HASH.equals(hash);
    }

    @Override
    public void run(ApplicationArguments args) {
        var hashes = jdbcTemplate.queryForList(
                "SELECT password_hash FROM user WHERE username = ?", String.class, "admin");
        if (hashes.isEmpty() || !isSeededAdmin("admin", hashes.get(0))) return;

        if (bootstrapPassword == null || bootstrapPassword.length() < 24
                || !bootstrapPassword.matches(".*[A-Za-z].*")
                || !bootstrapPassword.matches(".*[0-9].*")) {
            throw new IllegalStateException("Seeded admin credential requires MRP_BOOTSTRAP_ADMIN_PASSWORD (24+ letters and digits)");
        }

        jdbcTemplate.update("UPDATE user SET password_hash = ?, need_change_password = 1 "
                        + "WHERE username = ? AND password_hash = ?",
                passwordEncoder.encode(bootstrapPassword), "admin", SEEDED_HASH);
    }
}
