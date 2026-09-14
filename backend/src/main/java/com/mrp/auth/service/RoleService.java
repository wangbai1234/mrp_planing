package com.mrp.auth.service;

import com.mrp.auth.domain.Role;
import com.mrp.auth.domain.RoleDataScope;
import com.mrp.auth.repository.RoleMapper;
import com.mrp.audit.service.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleService {

    private final RoleMapper roleMapper;
    private final PermissionCacheService cacheService;
    private final AuditService auditService;

    public RoleService(RoleMapper roleMapper,
                       PermissionCacheService cacheService,
                       AuditService auditService) {
        this.roleMapper = roleMapper;
        this.cacheService = cacheService;
        this.auditService = auditService;
    }

    public Role getById(Long id) {
        return roleMapper.selectById(id);
    }

    public List<Role> getAll() {
        return roleMapper.selectAll();
    }

    public List<Role> getActive() {
        return roleMapper.selectActive();
    }

    public List<Role> getByUserId(Long userId) {
        return roleMapper.selectByUserId(userId);
    }

    @Transactional
    public Role create(Role role, Long operatorId) {
        role.setStatus(Role.STATUS_ACTIVE);
        roleMapper.insert(role);

        auditService.log(operatorId, "ADMIN", null,
                "CREATE", "ROLE", role.getId().toString(),
                null, role, null, null);

        return role;
    }

    @Transactional
    public Role update(Long id, Role role, Long operatorId) {
        Role before = roleMapper.selectById(id);

        if ("ADMIN".equals(before.getCode())) {
            if (!Role.STATUS_ACTIVE.equals(role.getStatus())) {
                throw new IllegalStateException("系统管理员角色不能被禁用");
            }
        }

        role.setId(id);
        roleMapper.update(role);

        auditService.log(operatorId, "ADMIN", null,
                "UPDATE", "ROLE", id.toString(),
                before, role, null, null);

        return roleMapper.selectById(id);
    }

    @Transactional
    public void delete(Long id, Long operatorId) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }

        if ("ADMIN".equals(role.getCode())) {
            throw new IllegalStateException("系统管理员角色不能被删除");
        }

        List<Long> userIds = roleMapper.selectUserIdsByRoleId(id);
        roleMapper.deleteRolePermissions(id);
        roleMapper.deleteById(id);

        // Evict cache for all affected users
        userIds.forEach(cacheService::evictUserPermissions);

        auditService.log(operatorId, "ADMIN", null,
                "DELETE", "ROLE", id.toString(),
                role, null, null, null);
    }

    @Transactional
    public void updatePermissions(Long roleId, List<Long> permissionIds, Long operatorId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }

        List<Long> before = roleMapper.selectPermissionIdsByRoleId(roleId);

        roleMapper.deleteRolePermissions(roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            roleMapper.batchInsertRolePermissions(roleId, permissionIds);
        }

        // Evict cache for all users with this role
        List<Long> userIds = roleMapper.selectUserIdsByRoleId(roleId);
        userIds.forEach(cacheService::evictUserPermissions);

        auditService.log(operatorId, "ADMIN", null,
                "UPDATE_PERMISSIONS", "ROLE", roleId.toString(),
                before, permissionIds, null, null);
    }

    @Transactional
    public Role copy(Long sourceId, String newName, String newCode, Long operatorId) {
        Role source = roleMapper.selectById(sourceId);
        if (source == null) {
            throw new IllegalArgumentException("源角色不存在");
        }

        Role newRole = new Role();
        newRole.setCode(newCode);
        newRole.setName(newName);
        newRole.setDescription(source.getDescription() + " (副本)");
        newRole.setStatus(Role.STATUS_ACTIVE);
        roleMapper.insert(newRole);

        // Copy permissions
        List<Long> permissionIds = roleMapper.selectPermissionIdsByRoleId(sourceId);
        if (!permissionIds.isEmpty()) {
            roleMapper.batchInsertRolePermissions(newRole.getId(), permissionIds);
        }

        auditService.log(operatorId, "ADMIN", null,
                "COPY_ROLE", "ROLE", newRole.getId().toString(),
                source, newRole, null, null);

        return newRole;
    }

    public List<Long> getPermissionIds(Long roleId) {
        return roleMapper.selectPermissionIdsByRoleId(roleId);
    }
}
