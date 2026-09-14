package com.mrp.auth;

import com.mrp.MrpApiApplication;
import com.mrp.auth.domain.User;
import com.mrp.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MrpApiApplication.class)
@ActiveProfiles("local")
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void contextLoads() {
        assertNotNull(userService);
    }

    @Test
    void adminUserExists() {
        User admin = userService.getByUsername("admin");
        assertNotNull(admin, "Admin user should exist from seed data");
        assertEquals("admin", admin.getUsername());
        assertTrue(admin.getIsActive());
    }

    @Test
    void listUsers_shouldReturnAdmin() {
        var result = userService.list(null, null, null, 1, 20);
        assertNotNull(result);
        Object total = result.get("total");
        assertNotNull(total);
        assertTrue(((Number) total).longValue() >= 1, "Should have at least1 user (admin)");
    }

    @Test
    void getEffectivePermissions_shouldReturnAdminPermissions() {
        var permissions = userService.getEffectivePermissions(1L);
        assertNotNull(permissions);
        assertFalse(permissions.isEmpty(), "Admin should have permissions");
        assertTrue(permissions.contains("schedule:view"), "Admin should have schedule:view");
        assertTrue(permissions.contains("user:view"), "Admin should have user:view");
    }
}
