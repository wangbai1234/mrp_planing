package com.mrp.mrp.domain;

import java.time.LocalDateTime;

public class MrpCalcBatch {
    private Long id;
    private String factoryCode;
    private String status;
    private Long forecastVersionId;
    private Long inventorySnapshotId;
    private Long bomSnapshotId;
    private String bomSourceChecksum;
    private Long categoryRoleVersionId;
    private Long materialPolicyVersionId;
    private Long productionCalendarVersionId;
    private Long factoryWarehouseMappingVersionId;
    private Long stockPolicyVersionId;
    private Long businessWeekVersionId;
    private Long inventorySnapshotGroupId;
    private String decisionChecksum;
    private String ruleVersion;
    private String inputChecksum;
    private String resultChecksum;
    private Integer versionNo;
    private Long activeRunId;
    private LocalDateTime publishedAt;
    private String activeRequestKey;
    private Long revisionOfBatchId;
    private Integer revisionNo;
    private Long supersededByBatchId;
    private Integer issueCount;
    private Integer unresolvedCount;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFactoryCode() { return factoryCode; }
    public void setFactoryCode(String factoryCode) { this.factoryCode = factoryCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getForecastVersionId() { return forecastVersionId; }
    public void setForecastVersionId(Long forecastVersionId) { this.forecastVersionId = forecastVersionId; }
    public Long getInventorySnapshotId() { return inventorySnapshotId; }
    public void setInventorySnapshotId(Long inventorySnapshotId) { this.inventorySnapshotId = inventorySnapshotId; }
    public Long getBomSnapshotId() { return bomSnapshotId; }
    public void setBomSnapshotId(Long bomSnapshotId) { this.bomSnapshotId = bomSnapshotId; }
    public String getBomSourceChecksum() { return bomSourceChecksum; }
    public void setBomSourceChecksum(String bomSourceChecksum) { this.bomSourceChecksum = bomSourceChecksum; }
    public Long getCategoryRoleVersionId() { return categoryRoleVersionId; }
    public void setCategoryRoleVersionId(Long categoryRoleVersionId) { this.categoryRoleVersionId = categoryRoleVersionId; }
    public Long getMaterialPolicyVersionId() { return materialPolicyVersionId; }
    public void setMaterialPolicyVersionId(Long materialPolicyVersionId) { this.materialPolicyVersionId = materialPolicyVersionId; }
    public Long getProductionCalendarVersionId() { return productionCalendarVersionId; }
    public void setProductionCalendarVersionId(Long productionCalendarVersionId) { this.productionCalendarVersionId = productionCalendarVersionId; }
    public Long getFactoryWarehouseMappingVersionId() { return factoryWarehouseMappingVersionId; }
    public void setFactoryWarehouseMappingVersionId(Long factoryWarehouseMappingVersionId) { this.factoryWarehouseMappingVersionId = factoryWarehouseMappingVersionId; }
    public Long getStockPolicyVersionId() { return stockPolicyVersionId; }
    public void setStockPolicyVersionId(Long stockPolicyVersionId) { this.stockPolicyVersionId = stockPolicyVersionId; }
    public Long getBusinessWeekVersionId() { return businessWeekVersionId; }
    public void setBusinessWeekVersionId(Long businessWeekVersionId) { this.businessWeekVersionId = businessWeekVersionId; }
    public Long getInventorySnapshotGroupId() { return inventorySnapshotGroupId; }
    public void setInventorySnapshotGroupId(Long inventorySnapshotGroupId) { this.inventorySnapshotGroupId = inventorySnapshotGroupId; }
    public String getDecisionChecksum() { return decisionChecksum; }
    public void setDecisionChecksum(String decisionChecksum) { this.decisionChecksum = decisionChecksum; }
    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String ruleVersion) { this.ruleVersion = ruleVersion; }
    public String getInputChecksum() { return inputChecksum; }
    public void setInputChecksum(String inputChecksum) { this.inputChecksum = inputChecksum; }
    public String getResultChecksum() { return resultChecksum; }
    public void setResultChecksum(String resultChecksum) { this.resultChecksum = resultChecksum; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public Long getActiveRunId() { return activeRunId; }
    public void setActiveRunId(Long activeRunId) { this.activeRunId = activeRunId; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getActiveRequestKey() { return activeRequestKey; }
    public void setActiveRequestKey(String activeRequestKey) { this.activeRequestKey = activeRequestKey; }
    public Long getRevisionOfBatchId() { return revisionOfBatchId; }
    public void setRevisionOfBatchId(Long revisionOfBatchId) { this.revisionOfBatchId = revisionOfBatchId; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer revisionNo) { this.revisionNo = revisionNo; }
    public Long getSupersededByBatchId() { return supersededByBatchId; }
    public void setSupersededByBatchId(Long supersededByBatchId) { this.supersededByBatchId = supersededByBatchId; }
    public Integer getIssueCount() { return issueCount; }
    public void setIssueCount(Integer issueCount) { this.issueCount = issueCount; }
    public Integer getUnresolvedCount() { return unresolvedCount; }
    public void setUnresolvedCount(Integer unresolvedCount) { this.unresolvedCount = unresolvedCount; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
