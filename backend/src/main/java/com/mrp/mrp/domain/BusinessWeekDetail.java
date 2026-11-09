package com.mrp.mrp.domain;

import java.time.LocalDate;

public class BusinessWeekDetail {
    private Long id;
    private Long versionId;
    private LocalDate businessMonth;
    private String weekCode;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer sortNo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public LocalDate getBusinessMonth() { return businessMonth; }
    public void setBusinessMonth(LocalDate businessMonth) { this.businessMonth = businessMonth; }
    public String getWeekCode() { return weekCode; }
    public void setWeekCode(String weekCode) { this.weekCode = weekCode; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
