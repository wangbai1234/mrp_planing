package com.mrp.security;

import com.mrp.BaseIntegrationTest;
import com.mrp.mrp.repository.MrpMaterialPolicyNoticeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkbenchRoleScopeSeedIntegrationTest extends BaseIntegrationTest {
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired MrpMaterialPolicyNoticeMapper factoryAccess;

    @Test
    void plannerAndMaterialSeeAllFactoriesWithoutCrossRolePageAccess() {
        for (String roleCode : new String[]{"PLANNER", "MATERIAL"}) {
            Long roleId = jdbc.queryForObject("SELECT id FROM role WHERE code=?", Long.class, roleCode);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM role_data_scope WHERE role_id=? AND scope_type='ALL'", Integer.class, roleId));
            // Use an isolated user with only one role and no per-user factory grants.
            String username = "role-scope-" + roleCode;
            jdbc.update("INSERT INTO user(username,password_hash) VALUES(?, 'unused')", username);
            Long fixtureId = jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, username);
            try {
                jdbc.update("INSERT INTO user_role(user_id,role_id) VALUES(?,?)", fixtureId, roleId);
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_factory_scope WHERE user_id=?", Integer.class, fixtureId));
                assertEquals(1, factoryAccess.canReadFactory(fixtureId, "爱培科"));
                assertEquals(1, factoryAccess.canReadFactory(fixtureId, "永惠"));
            } finally {
                jdbc.update("DELETE FROM user_role WHERE user_id=?", fixtureId);
                jdbc.update("DELETE FROM user WHERE id=?", fixtureId);
            }
        }
        assertEquals(0, permissionCount("PLANNER", "mrp_shortage:view"));
        assertEquals(0, permissionCount("MATERIAL", "mrp_workspace:view"));
        assertEquals(1, permissionCount("PLANNER", "mrp_workspace:view"));
        assertEquals(1, permissionCount("MATERIAL", "mrp_shortage:view"));
    }

    private int permissionCount(String roleCode, String permissionCode) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM role_permission rp JOIN role r ON r.id=rp.role_id "
                + "JOIN permission p ON p.id=rp.permission_id WHERE r.code=? AND p.code=?", Integer.class,
                roleCode, permissionCode);
    }
}
