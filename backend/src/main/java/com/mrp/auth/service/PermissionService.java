package com.mrp.auth.service;

import com.mrp.auth.domain.Permission;
import com.mrp.auth.repository.PermissionMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private static final String CACHE_KEY_PREFIX = "user:permissions:";
    private static final long CACHE_TTL_MINUTES = 30;

    private final PermissionMapper permissionMapper;
    private final StringRedisTemplate redisTemplate;

    public PermissionService(PermissionMapper permissionMapper, StringRedisTemplate redisTemplate) {
        this.permissionMapper = permissionMapper;
        this.redisTemplate = redisTemplate;
    }

    public List<Permission> getPermissionTree() {
        List<Permission> all = permissionMapper.selectActive();
        return buildTree(all, null);
    }

    public List<Permission> getPermissionsByRoleId(Long roleId) {
        return permissionMapper.selectByRoleId(roleId);
    }

    public List<String> getPermissionCodesByUserId(Long userId) {
        return permissionMapper.selectCodesByUserId(userId);
    }

    public Permission getByCode(String code) {
        return permissionMapper.selectByCode(code);
    }

    public Permission getById(Long id) {
        return permissionMapper.selectById(id);
    }

    public List<Permission> getAll() {
        return permissionMapper.selectAll();
    }

    private List<Permission> buildTree(List<Permission> all, Long parentId) {
        return all.stream()
                .filter(p -> Objects.equals(p.getParentId(), parentId))
                .sorted(Comparator.comparingInt(p -> p.getSort() != null ? p.getSort() : 0))
                .peek(p -> {
                    // Children will be loaded on demand if needed
                })
                .toList();
    }

    public Map<Long, List<Permission>> getPermissionTreeMap() {
        List<Permission> all = permissionMapper.selectActive();
        return all.stream()
                .collect(Collectors.groupingBy(p -> p.getParentId() != null ? p.getParentId() : 0L));
    }
}
