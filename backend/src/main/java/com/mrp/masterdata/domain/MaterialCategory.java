package com.mrp.masterdata.domain;

import java.time.Instant;
import java.util.List;

public class MaterialCategory {

    private Long id;
    private String code;
    private String name;
    private Long parentId;
    private Integer level;
    private Integer sort;
    private Boolean enabled;
    private Boolean isDeleted;
    private Instant createdAt;
    private Instant updatedAt;

    // Tree structure support
    private List<MaterialCategory> children;

    public MaterialCategory() {}

    public MaterialCategory(Long id, String code, String name, Long parentId, Integer level,
                            Integer sort, Boolean enabled, Boolean isDeleted,
                            Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.parentId = parentId;
        this.level = level;
        this.sort = sort;
        this.enabled = enabled;
        this.isDeleted = isDeleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<MaterialCategory> getChildren() { return children; }
    public void setChildren(List<MaterialCategory> children) { this.children = children; }
}
