package com.mrp.mrp.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class BomSnapshot {
    private Long id;
    private String sourceVersion;
    private String status;
    private String checksum;
    private String errorMessage;
    private LocalDate effectiveAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(String sourceVersion) { this.sourceVersion = sourceVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDate getEffectiveAt() { return effectiveAt; }
    public void setEffectiveAt(LocalDate effectiveAt) { this.effectiveAt = effectiveAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
