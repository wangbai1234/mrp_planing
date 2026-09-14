package com.mrp.auth.domain;

import java.time.Instant;

public class Role {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    private Long id;
    private String code;
    private String name;
    private String description;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    // Transient fields
    private Integer userCount;

    public Role() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Integer getUserCount() { return userCount; }
    public void setUserCount(Integer userCount) { this.userCount = userCount; }
}
