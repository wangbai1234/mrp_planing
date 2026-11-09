package com.mrp.mrp.domain;

import java.time.LocalDateTime;

public class MrpDecisionRecord {
    private Long id;
    private Long issueId;
    private String decisionCode;
    private String decisionPayload;
    private String reason;
    private Long operatorId;
    private Integer expectedRevision;
    private String decisionChecksum;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public String getDecisionCode() { return decisionCode; }
    public void setDecisionCode(String decisionCode) { this.decisionCode = decisionCode; }
    public String getDecisionPayload() { return decisionPayload; }
    public void setDecisionPayload(String decisionPayload) { this.decisionPayload = decisionPayload; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public Integer getExpectedRevision() { return expectedRevision; }
    public void setExpectedRevision(Integer expectedRevision) { this.expectedRevision = expectedRevision; }
    public String getDecisionChecksum() { return decisionChecksum; }
    public void setDecisionChecksum(String decisionChecksum) { this.decisionChecksum = decisionChecksum; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
