package com.mrp.bom.domain;

import java.time.LocalDate;

public class BomDetail {
    private String parentCode;
    private String parentName;
    private String childCode;
    private String childName;
    private String version;
    private Long parentQty;
    private LocalDate effectiveDate;
    private String materialSpec;
    private String makeFactory;
    private Long childQty;
    private String childNote;
    private String replacePriority;
    private String altCode;
    private String altName;
    private String altSpec;
    private String altFactory;
    private Long altQty;
    private String isDeliver;
    private String isDefault;
    private String mainStatus;
    private String altStatus;
    private String mainRhosStatus;
    private String altRhosStatus;
    private String admitNote;
    private String orgName;
    private String bodyCode;
    private Long materialCategoryId;
    private String materialCategoryName;

    public BomDetail() {}

    public String getParentCode() { return parentCode; }
    public void setParentCode(String parentCode) { this.parentCode = parentCode; }

    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }

    public String getChildCode() { return childCode; }
    public void setChildCode(String childCode) { this.childCode = childCode; }

    public String getChildName() { return childName; }
    public void setChildName(String childName) { this.childName = childName; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public Long getParentQty() { return parentQty; }
    public void setParentQty(Long parentQty) { this.parentQty = parentQty; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public String getMaterialSpec() { return materialSpec; }
    public void setMaterialSpec(String materialSpec) { this.materialSpec = materialSpec; }

    public String getMakeFactory() { return makeFactory; }
    public void setMakeFactory(String makeFactory) { this.makeFactory = makeFactory; }

    public Long getChildQty() { return childQty; }
    public void setChildQty(Long childQty) { this.childQty = childQty; }

    public String getChildNote() { return childNote; }
    public void setChildNote(String childNote) { this.childNote = childNote; }

    public String getReplacePriority() { return replacePriority; }
    public void setReplacePriority(String replacePriority) { this.replacePriority = replacePriority; }

    public String getAltCode() { return altCode; }
    public void setAltCode(String altCode) { this.altCode = altCode; }

    public String getAltName() { return altName; }
    public void setAltName(String altName) { this.altName = altName; }

    public String getAltSpec() { return altSpec; }
    public void setAltSpec(String altSpec) { this.altSpec = altSpec; }

    public String getAltFactory() { return altFactory; }
    public void setAltFactory(String altFactory) { this.altFactory = altFactory; }

    public Long getAltQty() { return altQty; }
    public void setAltQty(Long altQty) { this.altQty = altQty; }

    public String getIsDeliver() { return isDeliver; }
    public void setIsDeliver(String isDeliver) { this.isDeliver = isDeliver; }

    public String getIsDefault() { return isDefault; }
    public void setIsDefault(String isDefault) { this.isDefault = isDefault; }

    public String getMainStatus() { return mainStatus; }
    public void setMainStatus(String mainStatus) { this.mainStatus = mainStatus; }

    public String getAltStatus() { return altStatus; }
    public void setAltStatus(String altStatus) { this.altStatus = altStatus; }

    public String getMainRhosStatus() { return mainRhosStatus; }
    public void setMainRhosStatus(String mainRhosStatus) { this.mainRhosStatus = mainRhosStatus; }

    public String getAltRhosStatus() { return altRhosStatus; }
    public void setAltRhosStatus(String altRhosStatus) { this.altRhosStatus = altRhosStatus; }

    public String getAdmitNote() { return admitNote; }
    public void setAdmitNote(String admitNote) { this.admitNote = admitNote; }

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }

    public String getBodyCode() { return bodyCode; }
    public void setBodyCode(String bodyCode) { this.bodyCode = bodyCode; }

    public Long getMaterialCategoryId() { return materialCategoryId; }
    public void setMaterialCategoryId(Long materialCategoryId) { this.materialCategoryId = materialCategoryId; }

    public String getMaterialCategoryName() { return materialCategoryName; }
    public void setMaterialCategoryName(String materialCategoryName) { this.materialCategoryName = materialCategoryName; }
}
