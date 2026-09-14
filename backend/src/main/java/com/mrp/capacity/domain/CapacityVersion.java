package com.mrp.capacity.domain;

import java.time.Instant;

public class CapacityVersion {

    public static final String STATUS_ACTIVE = "ACTIVE";

    private Long id;
    private Integer versionNo;
    private String status;
    private Long createdBy;
    private Instant createdAt;

    public CapacityVersion() {}

    public CapacityVersion(Long id, Integer versionNo, String status, Long createdBy, Instant createdAt) {
        this.id = id;
        this.versionNo = versionNo;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long id() { return id; }
    public Integer versionNo() { return versionNo; }
    public String status() { return status; }
    public Long createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }

    public Long getId() { return id; }
    public Integer getVersionNo() { return versionNo; }
    public String getStatus() { return status; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
