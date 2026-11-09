package com.mrp.mrp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BomSnapshotDetail {
    private Long id;
    private Long snapshotId;
    private String parentCode;
    private String childCode;
    private BigDecimal parentQty;
    private BigDecimal childQty;
    private String bomVersion;
    private LocalDate effectiveDate;
    private String organizationCode;
    private String bodyScope;
    private String pathMetadata;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public String getParentCode() { return parentCode; }
    public void setParentCode(String parentCode) { this.parentCode = parentCode; }
    public String getChildCode() { return childCode; }
    public void setChildCode(String childCode) { this.childCode = childCode; }
    public BigDecimal getParentQty() { return parentQty; }
    public void setParentQty(BigDecimal parentQty) { this.parentQty = parentQty; }
    public BigDecimal getChildQty() { return childQty; }
    public void setChildQty(BigDecimal childQty) { this.childQty = childQty; }
    public String getBomVersion() { return bomVersion; }
    public void setBomVersion(String bomVersion) { this.bomVersion = bomVersion; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getOrganizationCode() { return organizationCode; }
    public void setOrganizationCode(String organizationCode) { this.organizationCode = organizationCode; }
    public String getBodyScope() { return bodyScope; }
    public void setBodyScope(String bodyScope) { this.bodyScope = bodyScope; }
    public String getPathMetadata() { return pathMetadata; }
    public void setPathMetadata(String pathMetadata) { this.pathMetadata = pathMetadata; }
}
