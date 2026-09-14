package com.mrp.auth.domain;

import java.time.Instant;

public class RoleDataScope {

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_FACTORY = "FACTORY";
    public static final String SCOPE_WAREHOUSE = "WAREHOUSE";
    public static final String SCOPE_LINE = "LINE";
    public static final String SCOPE_SELF = "SELF";

    private Long id;
    private Long roleId;
    private String scopeType;
    private String scopeValue;
    private Instant createdAt;

    public RoleDataScope() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public String getScopeValue() { return scopeValue; }
    public void setScopeValue(String scopeValue) { this.scopeValue = scopeValue; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
