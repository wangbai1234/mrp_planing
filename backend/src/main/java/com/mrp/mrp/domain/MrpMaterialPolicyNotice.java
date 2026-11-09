package com.mrp.mrp.domain;

public class MrpMaterialPolicyNotice {
    private Long id;
    private Long batchId;
    private Long runId;
    private String factoryCode;
    private String materialCode;
    private String materialName;
    private String missingType;
    private String effectiveExplosionPolicy;
    private String sourcesJson;
    private Long defaultExplosionConfirmedBy;
    private java.time.LocalDateTime defaultExplosionConfirmedAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long value) { batchId = value; }
    public Long getRunId() { return runId; }
    public void setRunId(Long value) { runId = value; }
    public String getFactoryCode() { return factoryCode; }
    public void setFactoryCode(String value) { factoryCode = value; }
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String value) { materialCode = value; }
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String value) { materialName = value; }
    public String getMissingType() { return missingType; }
    public void setMissingType(String value) { missingType = value; }
    public String getEffectiveExplosionPolicy() { return effectiveExplosionPolicy; }
    public void setEffectiveExplosionPolicy(String value) { effectiveExplosionPolicy = value; }
    public String getSourcesJson() { return sourcesJson; }
    public void setSourcesJson(String value) { sourcesJson = value; }
    public Long getDefaultExplosionConfirmedBy() { return defaultExplosionConfirmedBy; }
    public void setDefaultExplosionConfirmedBy(Long value) { defaultExplosionConfirmedBy = value; }
    public java.time.LocalDateTime getDefaultExplosionConfirmedAt() { return defaultExplosionConfirmedAt; }
    public void setDefaultExplosionConfirmedAt(java.time.LocalDateTime value) { defaultExplosionConfirmedAt = value; }
}
