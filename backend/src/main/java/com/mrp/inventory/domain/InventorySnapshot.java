package com.mrp.inventory.domain;

import java.time.Instant;
import java.time.LocalDate;

public class InventorySnapshot {

    public static final String STATUS_IMPORTED = "IMPORTED";

    private Long id;
    private LocalDate snapshotDate;
    private String fileName;
    private String fileChecksum;
    private String status;
    private Long createdBy;
    private Instant createdAt;

    public InventorySnapshot() {}

    public InventorySnapshot(Long id, LocalDate snapshotDate, String fileName, String fileChecksum,
                             String status, Long createdBy, Instant createdAt) {
        this.id = id;
        this.snapshotDate = snapshotDate;
        this.fileName = fileName;
        this.fileChecksum = fileChecksum;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileChecksum() { return fileChecksum; }
    public void setFileChecksum(String fileChecksum) { this.fileChecksum = fileChecksum; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
