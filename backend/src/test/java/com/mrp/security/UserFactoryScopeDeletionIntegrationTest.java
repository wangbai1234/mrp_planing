package com.mrp.security;

import com.mrp.BaseIntegrationTest;
import com.mrp.auth.domain.User;
import com.mrp.auth.service.UserService;
import com.mrp.common.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class UserFactoryScopeDeletionIntegrationTest extends BaseIntegrationTest {
    @Autowired UserService service;
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;

    @Test void deletingScopedUserRemovesOnlyTheirGrantsAndRetainsAudit() throws Exception {
        User target=create(), other=create();
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'爱培科'),(?,'永惠'),(?,'永惠')",target.getId(),target.getId(),other.getId());
        Long role=jdbc.queryForObject("SELECT id FROM role WHERE code='READONLY'",Long.class);
        jdbc.update("INSERT INTO user_role(user_id,role_id) VALUES(?,?)",target.getId(),role);
        var auth=new UsernamePasswordAuthenticationToken(new CurrentUser.UserPrincipal(1L,"admin"),null,List.of(new SimpleGrantedAuthority("user:delete")));
        mvc.perform(delete("/api/v1/users/"+target.getId()).with(authentication(auth))).andExpect(status().isOk());
        assertNull(service.getById(target.getId()));
        assertEquals(0,count("user_factory_scope",target.getId()));
        assertEquals(0,count("user_role",target.getId()));
        assertEquals(1,count("user_factory_scope",other.getId()));
        assertNotNull(service.getById(other.getId()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='DELETE' AND resource_type='USER' AND resource_id=?",Integer.class,target.getId().toString()));
    }

    @Test void lackingDeletePermissionCannotRemoveUserOrFactoryScope() throws Exception {
        User target=create();
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'爱培科')",target.getId());
        var auth=new UsernamePasswordAuthenticationToken(new CurrentUser.UserPrincipal(target.getId(),target.getUsername()),null,List.of(new SimpleGrantedAuthority("user:view")));
        mvc.perform(delete("/api/v1/users/"+target.getId()).with(authentication(auth))).andExpect(status().isForbidden());
        assertNotNull(service.getById(target.getId()));
        assertEquals(1,count("user_factory_scope",target.getId()));
    }

    private User create() {
        User user=new User();user.setUsername("delete-scope-"+UUID.randomUUID());
        return service.create(user,List.of(),1L).user();
    }
    private int count(String table,Long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE user_id=?",Integer.class,id);
    }
}
