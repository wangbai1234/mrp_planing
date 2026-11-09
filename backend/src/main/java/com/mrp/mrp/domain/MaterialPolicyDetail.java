package com.mrp.mrp.domain;

public class MaterialPolicyDetail {
    private Long id;
    private Long versionId;
    private String materialCode;
    private String supplyMode;
    private String explosionPolicy;
    private Integer revision;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    public String getSupplyMode() { return supplyMode; }
    public void setSupplyMode(String supplyMode) { this.supplyMode = supplyMode; }
    public String getExplosionPolicy() { return explosionPolicy; }
    public void setExplosionPolicy(String explosionPolicy) { this.explosionPolicy = explosionPolicy; }
    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }
}
