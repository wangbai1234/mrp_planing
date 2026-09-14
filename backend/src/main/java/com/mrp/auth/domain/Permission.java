package com.mrp.auth.domain;

import java.time.Instant;

public class Permission {

    public static final String TYPE_MENU = "MENU";
    public static final String TYPE_PAGE = "PAGE";
    public static final String TYPE_BUTTON = "BUTTON";
    public static final String TYPE_DATA = "DATA";
    public static final String STATUS_ACTIVE = "ACTIVE";

    private Long id;
    private String code;
    private String name;
    private String type;
    private Long parentId;
    private String resource;
    private String action;
    private Integer sort;
    private String status;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;

    public Permission() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
