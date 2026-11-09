package com.mrp.mrp.domain;

import java.time.LocalDate;

public class BusinessWeek extends ConfigAudit {
    private LocalDate businessMonth;
    private String weekCode;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer sortNo;

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
