package com.mrp.bom.domain;

import java.time.LocalDateTime;

public class BomSyncTask {
    private Long id;
    private String status;
    private String phase;
    private Long totalRows;
    private Long syncedRows;
    private Long parentCount;
    private Long zeroQty;
    private Long selfReference;
    private Boolean dataValid;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public Long getTotalRows() { return totalRows; }
    public void setTotalRows(Long totalRows) { this.totalRows = totalRows; }
    public Long getSyncedRows() { return syncedRows; }
    public void setSyncedRows(Long syncedRows) { this.syncedRows = syncedRows; }
    public Long getParentCount() { return parentCount; }
    public void setParentCount(Long parentCount) { this.parentCount = parentCount; }
    public Long getZeroQty() { return zeroQty; }
    public void setZeroQty(Long zeroQty) { this.zeroQty = zeroQty; }
    public Long getSelfReference() { return selfReference; }
    public void setSelfReference(Long selfReference) { this.selfReference = selfReference; }
    public Boolean getDataValid() { return dataValid; }
    public void setDataValid(Boolean dataValid) { this.dataValid = dataValid; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
