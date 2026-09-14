package com.mrp.auth.service;

import com.mrp.auth.domain.User;
import com.mrp.auth.domain.UserPermission;
import com.mrp.auth.repository.RoleMapper;
import com.mrp.auth.repository.UserMapper;
import com.mrp.auth.repository.UserPermissionMapper;
import com.mrp.audit.service.AuditService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final String DEFAULT_PASSWORD = "Mrp@123456";

    private final UserMapper userMapper;
    private final UserPermissionMapper userPermissionMapper;
    private final RoleMapper roleMapper;
    private final PermissionService permissionService;
    private final PermissionCacheService cacheService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserMapper userMapper,
                       UserPermissionMapper userPermissionMapper,
                       RoleMapper roleMapper,
                       PermissionService permissionService,
                       PermissionCacheService cacheService,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userMapper = userMapper;
        this.userPermissionMapper = userPermissionMapper;
        this.roleMapper = roleMapper;
        this.permissionService = permissionService;
        this.cacheService = cacheService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    public User getByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    public Map<String, Object> list(String keyword, Boolean isActive, Long roleId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<User> users = userMapper.selectWithRoles(keyword, isActive, roleId, offset, pageSize);
        for (User user : users) {
            user.setRoles(roleMapper.selectByUserId(user.getId()));
        }
        int total = userMapper.countWithRoles(keyword, isActive, roleId);
        return Map.of("items", users, "total", total, "page", page, "pageSize", pageSize);
    }

    @Transactional
    public User create(User user, List<Long> roleIds, Long operatorId) {
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setIsActive(true);
        userMapper.insert(user);

        if (roleIds != null) {
            for (Long roleId : roleIds) {
                roleMapper.insertUserRole(user.getId(), roleId);
            }
        }

        auditService.log(operatorId, "ADMIN", null,
                "CREATE", "USER", user.getId().toString(),
                null, user, null, null);

        return user;
    }

    @Transactional
    public User update(Long id, User user, Long operatorId) {
        User before = userMapper.selectById(id);
        user.setId(id);
        userMapper.update(user);

        auditService.log(operatorId, "ADMIN", null,
                "UPDATE", "USER", id.toString(),
                before, user, null, null);

        return userMapper.selectById(id);
    }

    @Transactional
    public void updateStatus(Long id, boolean isActive, Long operatorId) {
        User before = userMapper.selectById(id);

        if (!isActive) {
            boolean hasActiveAdmin = userMapper.hasActiveAdminExcluding(id);
            List<com.mrp.auth.domain.Role> roles = getUserRoles(id);
            boolean isAdmin = roles.stream().anyMatch(r -> "ADMIN".equals(r.getCode()));
            if (isAdmin && !hasActiveAdmin) {
                throw new IllegalStateException("不能禁用最后一个系统管理员");
            }
        }

        userMapper.updateStatus(id, isActive);
        cacheService.evictUserPermissions(id);

        auditService.log(operatorId, "ADMIN", null,
                isActive ? "ENABLE" : "DISABLE", "USER", id.toString(),
                before, userMapper.selectById(id), null, null);
    }

    @Transactional
    public void resetPassword(Long id, Long operatorId) {
        String hash = passwordEncoder.encode(DEFAULT_PASSWORD);
        userMapper.updatePassword(id, hash);

        auditService.log(operatorId, "ADMIN", null,
                "RESET_PASSWORD", "USER", id.toString(),
                null, null, null, null);
    }

    @Transactional
    public void delete(Long id, Long operatorId) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        boolean hasActiveAdmin = userMapper.hasActiveAdminExcluding(id);
        List<com.mrp.auth.domain.Role> roles = getUserRoles(id);
        boolean isAdmin = roles.stream().anyMatch(r -> "ADMIN".equals(r.getCode()));
        if (isAdmin && !hasActiveAdmin) {
            throw new IllegalStateException("不能删除最后一个系统管理员");
        }

        roleMapper.deleteUserRoles(id);
        userPermissionMapper.deleteByUserId(id);
        userMapper.deleteById(id);
        cacheService.evictUserPermissions(id);

        auditService.log(operatorId, "ADMIN", null,
                "DELETE", "USER", id.toString(),
                user, null, null, null);
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds, Long operatorId) {
        List<com.mrp.auth.domain.Role> beforeRoles = getUserRoles(userId);

        roleMapper.deleteUserRoles(userId);
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                roleMapper.insertUserRole(userId, roleId);
                cacheService.addRoleUser(roleId, userId);
            }
        }

        cacheService.evictUserPermissions(userId);

        auditService.log(operatorId, "ADMIN", null,
                "ASSIGN_ROLE", "USER", userId.toString(),
                beforeRoles, roleIds, null, null);
    }

    @Transactional
    public void updateUserPermissions(Long userId, List<UserPermission> permissions, Long operatorId) {
        List<UserPermission> before = userPermissionMapper.selectByUserId(userId);

        userPermissionMapper.deleteByUserId(userId);
        if (permissions != null && !permissions.isEmpty()) {
            for (UserPermission up : permissions) {
                up.setUserId(userId);
                up.setCreatedBy(operatorId);
            }
            userPermissionMapper.batchInsert(permissions);
        }

        cacheService.evictUserPermissions(userId);

        auditService.log(operatorId, "ADMIN", null,
                "UPDATE_PERMISSIONS", "USER", userId.toString(),
                before, permissions, null, null);
    }

    public List<com.mrp.auth.domain.Role> getUserRoles(Long userId) {
        return roleMapper.selectByUserId(userId);
    }

    public Set<String> getEffectivePermissions(Long userId) {
        List<String> cached = cacheService.getCachedPermissions(userId);
        if (cached != null) {
            return new HashSet<>(cached);
        }

        Set<String> effective = new HashSet<>();

        // Role permissions
        List<String> rolePerms = permissionService.getPermissionCodesByUserId(userId);
        effective.addAll(rolePerms);

        // User direct permissions
        List<UserPermission> userPerms = userPermissionMapper.selectByUserId(userId);
        for (UserPermission up : userPerms) {
            if (UserPermission.EFFECT_ALLOW.equals(up.getEffect())) {
                effective.add(up.getPermissionCode());
            } else if (UserPermission.EFFECT_DENY.equals(up.getEffect())) {
                effective.remove(up.getPermissionCode());
            }
        }

        cacheService.cachePermissions(userId, new ArrayList<>(effective));
        return effective;
    }

    public Map<String, Object> getUserPermissionDetail(Long userId) {
        List<String> rolePerms = permissionService.getPermissionCodesByUserId(userId);
        List<UserPermission> userPerms = userPermissionMapper.selectByUserId(userId);

        Set<String> allowCodes = userPerms.stream()
                .filter(up -> UserPermission.EFFECT_ALLOW.equals(up.getEffect()))
                .map(UserPermission::getPermissionCode)
                .collect(Collectors.toSet());

        Set<String> denyCodes = userPerms.stream()
                .filter(up -> UserPermission.EFFECT_DENY.equals(up.getEffect()))
                .map(UserPermission::getPermissionCode)
                .collect(Collectors.toSet());

        Set<String> effective = getEffectivePermissions(userId);

        return Map.of(
                "rolePermissions", rolePerms,
                "userAllowPermissions", allowCodes,
                "userDenyPermissions", denyCodes,
                "effectivePermissions", effective
        );
    }
}
