package com.mrp.mrp.domain;

import java.time.LocalDateTime;

public class MrpCalcRun {
    private Long id;
    private Long batchId;
    private String status;
    private String resultChecksum;
    private String rowCountsJson;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorCode;
    private String errorMessage;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResultChecksum() { return resultChecksum; }
    public void setResultChecksum(String resultChecksum) { this.resultChecksum = resultChecksum; }
    public String getRowCountsJson() { return rowCountsJson; }
    public void setRowCountsJson(String rowCountsJson) { this.rowCountsJson = rowCountsJson; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
