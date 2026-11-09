package com.mrp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.auth.domain.User;
import com.mrp.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class UserSerializationTest {
    @Test void excludesPasswordFromResponsesAndNestedAuditValues() throws Exception {
        var user = new User();
        user.setId(1L); user.setUsername("test"); user.setPasswordHash("sensitive-test-value");
        var mapper = new ObjectMapper().findAndRegisterModules();
        for (Object value : new Object[]{user, ApiResponse.ok(user), Map.of("before", user, "after", user)}) {
            String json = mapper.writeValueAsString(value);
            assertFalse(json.contains("passwordHash"));
            assertFalse(json.contains("sensitive-test-value"));
            assertTrue(json.contains("username"));
        }
        assertEquals("sensitive-test-value", user.getPasswordHash(), "Authentication still needs the stored value");
    }
}
