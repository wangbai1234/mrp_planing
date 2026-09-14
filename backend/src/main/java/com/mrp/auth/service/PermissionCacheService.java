package com.mrp.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class PermissionCacheService {

    private static final String CACHE_KEY_PREFIX = "user:permissions:";
    private static final String ROLE_USERS_PREFIX = "role:users:";
    private static final long CACHE_TTL_MINUTES = 30;

    private final StringRedisTemplate redisTemplate;

    public PermissionCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public List<String> getCachedPermissions(Long userId) {
        String key = CACHE_KEY_PREFIX + userId;
        List<String> cached = redisTemplate.opsForList().range(key, 0, -1);
        return (cached != null && !cached.isEmpty()) ? cached : null;
    }

    public void cachePermissions(Long userId, List<String> permissions) {
        String key = CACHE_KEY_PREFIX + userId;
        redisTemplate.delete(key);
        if (permissions != null && !permissions.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(key, permissions);
            redisTemplate.expire(key, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        }
    }

    public void evictUserPermissions(Long userId) {
        String key = CACHE_KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }

    public void evictRoleUsers(Long roleId) {
        String key = ROLE_USERS_PREFIX + roleId;
        Set<String> userIds = redisTemplate.opsForSet().members(key);
        if (userIds != null) {
            userIds.forEach(uid -> evictUserPermissions(Long.parseLong(uid)));
        }
        redisTemplate.delete(key);
    }

    public void addRoleUser(Long roleId, Long userId) {
        String key = ROLE_USERS_PREFIX + roleId;
        redisTemplate.opsForSet().add(key, userId.toString());
        redisTemplate.expire(key, CACHE_TTL_MINUTES * 2, TimeUnit.MINUTES);
    }

    public void removeRoleUser(Long roleId, Long userId) {
        String key = ROLE_USERS_PREFIX + roleId;
        redisTemplate.opsForSet().remove(key, userId.toString());
    }
}
