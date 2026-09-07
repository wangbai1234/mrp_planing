package com.mrp.bom.domain;

public class BomMaterial {
    private String invCode;
    private String invName;
    private String version;
    private String unit;
    private String orgName;
    private String bodyCode;
    private Long parentQty;
    private Long childCount;

    public BomMaterial() {}

    public String getInvCode() { return invCode; }
    public void setInvCode(String invCode) { this.invCode = invCode; }

    public String getInvName() { return invName; }
    public void setInvName(String invName) { this.invName = invName; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }

    public String getBodyCode() { return bodyCode; }
    public void setBodyCode(String bodyCode) { this.bodyCode = bodyCode; }

    public Long getParentQty() { return parentQty; }
    public void setParentQty(Long parentQty) { this.parentQty = parentQty; }

    public Long getChildCount() { return childCount; }
    public void setChildCount(Long childCount) { this.childCount = childCount; }
}
