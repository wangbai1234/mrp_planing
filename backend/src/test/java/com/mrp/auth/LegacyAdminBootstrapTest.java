package com.mrp.auth;

import com.mrp.auth.service.LegacyAdminBootstrap;
import com.mrp.auth.controller.AuthController;
import com.mrp.auth.service.FeishuService;
import com.mrp.auth.service.JwtService;
import com.mrp.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyAdminBootstrapTest {

    private static final String SEEDED_HASH = "$2b$10$F17Gmp5YjVbFVtsop3Eb6OITyzwSqepFInIulR6ZOFXEX69OeYc22";

    @Test
    void seededAdminRequiresStrongBootstrapPassword() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("admin"))).thenReturn(List.of(SEEDED_HASH));

        var bootstrap = new LegacyAdminBootstrap(jdbc, mock(PasswordEncoder.class), "");
        assertThrows(IllegalStateException.class, () -> bootstrap.run(new DefaultApplicationArguments()));
        verify(jdbc, never()).update(anyString(), any(), any(), any());
        assertTrue(LegacyAdminBootstrap.isSeededAdmin("admin", SEEDED_HASH));
        assertFalse(LegacyAdminBootstrap.isSeededAdmin("other", SEEDED_HASH));
    }

    @Test
    void onlyExactSeededHashIsRotated() {
        var jdbc = mock(JdbcTemplate.class);
        var encoder = mock(PasswordEncoder.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("admin"))).thenReturn(List.of(SEEDED_HASH));
        when(encoder.encode(anyString())).thenReturn("rotated-hash");
        var bootstrap = new LegacyAdminBootstrap(jdbc, encoder, "secure-test-bootstrap-password-123");

        bootstrap.run(new DefaultApplicationArguments());
        verify(jdbc).update(contains("WHERE username = ? AND password_hash = ?"),
                eq("rotated-hash"), eq("admin"), eq(SEEDED_HASH));

        reset(jdbc, encoder);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("admin"))).thenReturn(List.of("user-rotated-hash"));
        bootstrap.run(new DefaultApplicationArguments());
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test
    void loginRejectsSeededHashEvenWhenPasswordEncoderWouldAcceptIt() {
        var jdbc = mock(JdbcTemplate.class);
        var encoder = mock(PasswordEncoder.class);
        when(jdbc.queryForList(anyString(), eq("admin"), eq("admin")))
                .thenReturn(List.of(Map.of("username", "admin", "password_hash", SEEDED_HASH, "is_active", true)));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);
        var controller = new AuthController(jdbc, encoder, mock(JwtService.class),
                mock(UserService.class), mock(FeishuService.class));

        var response = controller.login(Map.of("username", "admin", "password", "old-value"));
        assertFalse(response.success());
        assertEquals("MRP_AUTH_ERROR", response.error().code());
        verify(encoder, never()).matches(anyString(), anyString());
    }
}
