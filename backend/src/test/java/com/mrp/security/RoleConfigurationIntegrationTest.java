package com.mrp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.BaseIntegrationTest;
import com.mrp.auth.service.JwtService;
import com.mrp.auth.service.PermissionCacheService;
import com.mrp.mrp.repository.MrpMaterialPolicyNoticeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RoleConfigurationIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @Autowired PermissionCacheService permissionCache;
    @Autowired MrpMaterialPolicyNoticeMapper factoryAccess;

    @Test
    void referencedRoleMustBeUnassignedBeforeDeletion() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String roleCode = "DELETE_" + suffix;
        String username = "member_" + suffix;
        String adminHash = jdbc.queryForObject("SELECT password_hash FROM user WHERE username='admin'", String.class);
        String adminToken = jwt.generateToken(1L, "admin", adminHash);
        String created = mvc.perform(post("/api/v1/roles")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("code", roleCode, "name", roleCode))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long roleId = json.readTree(created).path("data").path("id").longValue();
        try {
            jdbc.update("INSERT INTO user(username,password_hash,need_change_password) VALUES(?,?,0)",
                    username, encoder.encode("member-test-only-123"));
            Long userId = jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, username);
            jdbc.update("INSERT INTO user_role(user_id,role_id) VALUES(?,?)", userId, roleId);

            mvc.perform(delete("/api/v1/roles/" + roleId).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("MRP_ILLEGAL_STATE"));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM role WHERE id=?", Integer.class, roleId));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_role WHERE role_id=?", Integer.class, roleId));

            jdbc.update("DELETE FROM user_role WHERE user_id=? AND role_id=?", userId, roleId);
            mvc.perform(delete("/api/v1/roles/" + roleId).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM role WHERE id=?", Integer.class, roleId));
        } finally {
            jdbc.update("DELETE FROM audit_log WHERE resource_type='ROLE' AND resource_id=?", roleId.toString());
            jdbc.update("DELETE FROM user_role WHERE role_id=?", roleId);
            jdbc.update("DELETE FROM user WHERE username=?", username);
            jdbc.update("DELETE FROM role_permission WHERE role_id=?", roleId);
            jdbc.update("DELETE FROM role WHERE id=?", roleId);
        }
    }

    @Test
    void creatingUserWithRoleRequiresAssignmentPermission() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String roleCode = "CREATE_" + suffix;
        String username = "creator_" + suffix;
        String createdName = "created_" + suffix;
        jdbc.update("INSERT INTO role(code,name,status) VALUES(?,?,'ACTIVE')", roleCode, roleCode);
        Long roleId = jdbc.queryForObject("SELECT id FROM role WHERE code=?", Long.class, roleCode);
        jdbc.update("INSERT INTO user(username,password_hash,need_change_password) VALUES(?,?,0)",
                username, encoder.encode("creator-test-only-123"));
        Long userId = jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, username);
        jdbc.update("INSERT INTO user_role(user_id,role_id) VALUES(?,?)", userId, roleId);
        jdbc.update("INSERT INTO role_permission(role_id,permission_id) SELECT ?,id FROM permission WHERE code='user:create'", roleId);
        String hash = jdbc.queryForObject("SELECT password_hash FROM user WHERE id=?", String.class, userId);
        String token = jwt.generateToken(userId, username, hash);
        Long adminRoleId = jdbc.queryForObject("SELECT id FROM role WHERE code='ADMIN'", Long.class);
        try {
            mvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("username", createdName, "roleIds", List.of(adminRoleId)))))
                    .andExpect(status().isForbidden());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE username=?", Integer.class, createdName));

            mvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("username", createdName, "roleIds", List.of()))))
                    .andExpect(status().isOk());
            Long createdId = jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, createdName);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_role WHERE user_id=?", Integer.class, createdId));
        } finally {
            jdbc.update("DELETE FROM audit_log WHERE operator_id=?", userId);
            jdbc.update("DELETE FROM user_role WHERE user_id IN (SELECT id FROM user WHERE username IN (?,?))", username, createdName);
            jdbc.update("DELETE FROM user WHERE username IN (?,?)", username, createdName);
            jdbc.update("DELETE FROM role_permission WHERE role_id=?", roleId);
            jdbc.update("DELETE FROM role WHERE id=?", roleId);
            permissionCache.evictUserPermissions(userId);
        }
    }

    @Test
    void rolePageConfiguresPermissionsAndFactoryScopeAndDisabledRoleRevokesExistingToken() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String roleCode = "ACCEPT_" + suffix;
        String username = "accept_" + suffix;
        String hash = encoder.encode("role-test-only-123");
        jdbc.update("INSERT INTO role(code,name,status) VALUES(?,?,'ACTIVE')", roleCode, roleCode);
        Long roleId = jdbc.queryForObject("SELECT id FROM role WHERE code=?", Long.class, roleCode);
        jdbc.update("INSERT INTO user(username,password_hash,need_change_password) VALUES(?,?,0)", username, hash);
        Long userId = jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, username);
        jdbc.update("INSERT INTO user_role(user_id,role_id) VALUES(?,?)", userId, roleId);
        String token = jwt.generateToken(userId, username, hash);
        String adminHash = jdbc.queryForObject("SELECT password_hash FROM user WHERE username='admin'", String.class);
        String adminToken = jwt.generateToken(1L, "admin", adminHash);
        Long viewId = jdbc.queryForObject("SELECT id FROM permission WHERE code='schedule:view'", Long.class);
        Long manageId = jdbc.queryForObject("SELECT id FROM permission WHERE code='role:assign_permission'", Long.class);
        try {
            Long adminRoleId = jdbc.queryForObject("SELECT id FROM role WHERE code='ADMIN'", Long.class);
            mvc.perform(put("/api/v1/roles/" + adminRoleId + "/permissions")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json").content("{\"permissionIds\":[]}"))
                    .andExpect(status().isBadRequest());
            configure(adminRoleId, adminToken, List.of(viewId), "ALL", List.of())
                    .andExpect(status().isBadRequest());
            configure(adminRoleId, adminToken, List.of(viewId, manageId), "FACTORY", List.of("爱培科"))
                    .andExpect(status().isBadRequest());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM role_permission WHERE role_id=? AND permission_id=?",
                    Integer.class, adminRoleId, manageId));
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.permissions").isEmpty());

            configure(roleId, adminToken, List.of(viewId, manageId), "FACTORY", List.of("爱培科"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.permissions.length()").value(2));
            mvc.perform(get("/api/v1/roles/" + roleId + "/data-scope").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.factoryCodes[0]").value("爱培科"));
            mvc.perform(get("/api/v1/permissions/tree").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            assertEquals(1, factoryAccess.canReadFactory(userId, "爱培科"));
            assertEquals(0, factoryAccess.canReadFactory(userId, "永惠"));

            configure(roleId, adminToken, List.of(viewId, manageId), "FACTORY", List.of("missing-factory"))
                    .andExpect(status().isBadRequest());
            assertEquals(1, factoryAccess.canReadFactory(userId, "爱培科"));
            configure(roleId, adminToken, List.of(viewId), "ALL", List.of()).andExpect(status().isOk());
            assertEquals(1, factoryAccess.canReadFactory(userId, "永惠"));
            mvc.perform(get("/api/v1/roles/" + roleId).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());

            mvc.perform(put("/api/v1/roles/" + roleId).header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json").content("{\"status\":\"DISABLED\"}"))
                    .andExpect(status().isOk());
            // A concurrent request may have read the old grant before the role change committed.
            permissionCache.cachePermissions(userId, List.of("schedule:view"));
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.permissions").isEmpty());
            mvc.perform(get("/api/v1/plans").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            assertEquals(0, factoryAccess.canReadFactory(userId, "爱培科"));
        } finally {
            jdbc.update("DELETE FROM audit_log WHERE resource_type='ROLE' AND resource_id=?", roleId.toString());
            jdbc.update("DELETE FROM role_permission WHERE role_id=?", roleId);
            jdbc.update("DELETE FROM role_data_scope WHERE role_id=?", roleId);
            jdbc.update("DELETE FROM user_role WHERE user_id=?", userId);
            jdbc.update("DELETE FROM user WHERE id=?", userId);
            jdbc.update("DELETE FROM role WHERE id=?", roleId);
            permissionCache.evictUserPermissions(userId);
        }
    }

    private org.springframework.test.web.servlet.ResultActions configure(Long roleId, String token,
            List<Long> permissionIds, String type, List<String> factories) throws Exception {
        return mvc.perform(put("/api/v1/roles/" + roleId + "/configuration")
                .header("Authorization", "Bearer " + token).contentType("application/json")
                .content(json.writeValueAsString(Map.of("permissionIds", permissionIds,
                        "dataScope", Map.of("scopeType", type, "factoryCodes", factories)))));
    }
}
