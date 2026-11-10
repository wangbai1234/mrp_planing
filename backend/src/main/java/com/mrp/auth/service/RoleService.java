package com.mrp.auth.service;

import com.mrp.auth.domain.Role;
import com.mrp.auth.domain.RoleDataScope;
import com.mrp.auth.repository.RoleMapper;
import com.mrp.audit.service.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

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

    public java.util.Map<String, Object> getPage(String keyword, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        String search = keyword == null ? "" : keyword.trim();
        return java.util.Map.of(
                "items", roleMapper.selectPage(search, (safePage - 1) * safeSize, safeSize),
                "total", roleMapper.countPage(search),
                "page", safePage,
                "pageSize", safeSize);
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
        if (before == null) throw new IllegalArgumentException("角色不存在");
        if (role.getName() == null) role.setName(before.getName());
        if (role.getDescription() == null) role.setDescription(before.getDescription());
        if (role.getStatus() == null) role.setStatus(before.getStatus());
        if (!Role.STATUS_ACTIVE.equals(role.getStatus()) && !Role.STATUS_DISABLED.equals(role.getStatus()))
            throw new IllegalArgumentException("无效的角色状态");

        if ("ADMIN".equals(before.getCode())) {
            if (!Role.STATUS_ACTIVE.equals(role.getStatus())) {
                throw new IllegalStateException("系统管理员角色不能被禁用");
            }
        }

        role.setId(id);
        roleMapper.update(role);

        if (!before.getStatus().equals(role.getStatus())) {
            roleMapper.selectUserIdsByRoleId(id).forEach(cacheService::evictUserPermissions);
        }

        auditService.log(operatorId, "ADMIN", null,
                "UPDATE", "ROLE", id.toString(),
                before, role, null, null);

        return roleMapper.selectById(id);
    }

    @Transactional
    public void delete(Long id, Long operatorId) {
        Role role = roleMapper.selectByIdForUpdate(id);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }

        if ("ADMIN".equals(role.getCode())) {
            throw new IllegalStateException("系统管理员角色不能被删除");
        }

        List<Long> userIds = roleMapper.selectUserIdsByRoleId(id);
        if (!userIds.isEmpty()) {
            throw new IllegalStateException("角色已关联用户，请先移除用户的角色分配");
        }
        roleMapper.deleteRolePermissions(id);
        roleMapper.deleteById(id);

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
        requireAdminPermissions(role, permissionIds);

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
        List<RoleDataScope> scopes = roleMapper.selectDataScopesByRoleId(sourceId);
        if (scopes.stream().anyMatch(s -> RoleDataScope.SCOPE_ALL.equals(s.getScopeType()))) {
            roleMapper.insertAllDataScope(newRole.getId());
        } else {
            List<String> factories = scopes.stream()
                    .filter(s -> RoleDataScope.SCOPE_FACTORY.equals(s.getScopeType()))
                    .map(RoleDataScope::getScopeValue).distinct().toList();
            if (!factories.isEmpty()) roleMapper.insertFactoryDataScopes(newRole.getId(), factories);
        }

        auditService.log(operatorId, "ADMIN", null,
                "COPY_ROLE", "ROLE", newRole.getId().toString(),
                source, newRole, null, null);

        return newRole;
    }

    public List<Long> getPermissionIds(Long roleId) {
        return roleMapper.selectPermissionIdsByRoleId(roleId);
    }

    public record DataScope(String scopeType, List<String> factoryCodes) {}

    public List<String> getEnabledFactoryCodes() {
        return roleMapper.selectEnabledFactoryCodes();
    }

    public DataScope getDataScope(Long roleId) {
        if (roleMapper.selectById(roleId) == null) throw new IllegalArgumentException("角色不存在");
        List<RoleDataScope> scopes = roleMapper.selectDataScopesByRoleId(roleId);
        if (scopes.stream().anyMatch(s -> RoleDataScope.SCOPE_ALL.equals(s.getScopeType()))) {
            return new DataScope("ALL", List.of());
        }
        List<String> factories = scopes.stream()
                .filter(s -> RoleDataScope.SCOPE_FACTORY.equals(s.getScopeType()))
                .map(RoleDataScope::getScopeValue).distinct().toList();
        if (scopes.size() != factories.size()) {
            throw new IllegalStateException("角色存在不支持在此页面编辑的数据范围");
        }
        return new DataScope(factories.isEmpty() ? "NONE" : "FACTORY", factories);
    }

    @Transactional
    public void updateConfiguration(Long roleId, List<Long> permissionIds, DataScope scope, Long operatorId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) throw new IllegalArgumentException("角色不存在");
        requireAdminPermissions(role, permissionIds);
        if (scope == null || scope.scopeType() == null) throw new IllegalArgumentException("请选择工厂可见范围");
        Set<String> factories = new LinkedHashSet<>(scope.factoryCodes() == null ? List.of() : scope.factoryCodes());
        switch (scope.scopeType()) {
            case "ALL", "NONE" -> {
                if (!factories.isEmpty()) throw new IllegalArgumentException("该范围不能指定工厂");
            }
            case "FACTORY" -> {
                if (factories.isEmpty() || !roleMapper.selectEnabledFactoryCodes().containsAll(factories))
                    throw new IllegalArgumentException("请选择有效工厂");
            }
            default -> throw new IllegalArgumentException("无效的工厂可见范围");
        }
        if ("ADMIN".equals(role.getCode()) && !"ALL".equals(scope.scopeType()))
            throw new IllegalArgumentException("系统管理员必须保留全部工厂范围");

        List<Long> beforePermissions = roleMapper.selectPermissionIdsByRoleId(roleId);
        DataScope beforeScope = getDataScope(roleId);
        roleMapper.deleteRolePermissions(roleId);
        if (permissionIds != null && !permissionIds.isEmpty())
            roleMapper.batchInsertRolePermissions(roleId, permissionIds);
        roleMapper.deleteDataScopesByRoleId(roleId);
        if ("ALL".equals(scope.scopeType())) roleMapper.insertAllDataScope(roleId);
        if ("FACTORY".equals(scope.scopeType())) roleMapper.insertFactoryDataScopes(roleId, List.copyOf(factories));
        roleMapper.selectUserIdsByRoleId(roleId).forEach(cacheService::evictUserPermissions);
        auditService.log(operatorId, "ADMIN", null, "UPDATE_PERMISSIONS", "ROLE", roleId.toString(),
                java.util.Map.of("permissions", beforePermissions, "dataScope", beforeScope),
                java.util.Map.of("permissions", permissionIds == null ? List.of() : permissionIds,
                        "dataScope", new DataScope(scope.scopeType(), List.copyOf(factories))), null, null);
    }

    private void requireAdminPermissions(Role role, List<Long> permissionIds) {
        if ("ADMIN".equals(role.getCode()) &&
                (permissionIds == null || !permissionIds.containsAll(roleMapper.selectActivePermissionIds())))
            throw new IllegalArgumentException("系统管理员必须保留全部有效权限");
    }
}
