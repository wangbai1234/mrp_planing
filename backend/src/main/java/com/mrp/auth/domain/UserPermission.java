package com.mrp.auth.domain;

import java.time.Instant;

public class UserPermission {

    public static final String EFFECT_ALLOW = "ALLOW";
    public static final String EFFECT_DENY = "DENY";

    private Long id;
    private Long userId;
    private Long permissionId;
    private String effect;
    private Instant createdAt;
    private Long createdBy;

    // Transient
    private String permissionCode;
    private String permissionName;

    public UserPermission() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }
    public String getEffect() { return effect; }
    public void setEffect(String effect) { this.effect = effect; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
    public String getPermissionName() { return permissionName; }
    public void setPermissionName(String permissionName) { this.permissionName = permissionName; }
}
