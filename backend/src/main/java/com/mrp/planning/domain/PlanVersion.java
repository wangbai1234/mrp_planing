package com.mrp.planning.domain;

import java.time.Instant;
import java.time.LocalDate;

public class PlanVersion {
    public static final String STATUS_CALCULATING = "CALCULATING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_FAILED = "FAILED";

    private Long id;
    private Integer versionNo;
    private String factoryCode;
    private Long forecastVersionId;
    private Long inventorySnapshotId;
    private Long shipmentBatchId;
    private Long capacityVersionId;
    private String ruleVersion;
    private LocalDate currentWeekStart;
    private String inputChecksum;
    private String resultChecksum;
    private String status;
    private Boolean autoRecalcForecast;
    private Boolean autoRecalcInventory;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public PlanVersion() {}

    public PlanVersion(Long id, Integer versionNo, String factoryCode, Long forecastVersionId,
                       Long inventorySnapshotId, Long shipmentBatchId, Long capacityVersionId,
                       String ruleVersion, LocalDate currentWeekStart, String inputChecksum,
                       String resultChecksum, String status, Boolean autoRecalcForecast,
                       Boolean autoRecalcInventory, Long createdBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.versionNo = versionNo;
        this.factoryCode = factoryCode;
        this.forecastVersionId = forecastVersionId;
        this.inventorySnapshotId = inventorySnapshotId;
        this.shipmentBatchId = shipmentBatchId;
        this.capacityVersionId = capacityVersionId;
        this.ruleVersion = ruleVersion;
        this.currentWeekStart = currentWeekStart;
        this.inputChecksum = inputChecksum;
        this.resultChecksum = resultChecksum;
        this.status = status;
        this.autoRecalcForecast = autoRecalcForecast;
        this.autoRecalcInventory = autoRecalcInventory;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long id() { return id; }
    public Integer versionNo() { return versionNo; }
    public String factoryCode() { return factoryCode; }
    public Long forecastVersionId() { return forecastVersionId; }
    public Long inventorySnapshotId() { return inventorySnapshotId; }
    public Long shipmentBatchId() { return shipmentBatchId; }
    public Long capacityVersionId() { return capacityVersionId; }
    public String ruleVersion() { return ruleVersion; }
    public LocalDate currentWeekStart() { return currentWeekStart; }
    public String inputChecksum() { return inputChecksum; }
    public String resultChecksum() { return resultChecksum; }
    public String status() { return status; }
    public Boolean autoRecalcForecast() { return autoRecalcForecast; }
    public Boolean autoRecalcInventory() { return autoRecalcInventory; }
    public Long createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    // Jackson getter methods
    public Long getId() { return id; }
    public Integer getVersionNo() { return versionNo; }
    public String getFactoryCode() { return factoryCode; }
    public Long getForecastVersionId() { return forecastVersionId; }
    public Long getInventorySnapshotId() { return inventorySnapshotId; }
    public Long getShipmentBatchId() { return shipmentBatchId; }
    public Long getCapacityVersionId() { return capacityVersionId; }
    public String getRuleVersion() { return ruleVersion; }
    public LocalDate getCurrentWeekStart() { return currentWeekStart; }
    public String getInputChecksum() { return inputChecksum; }
    public String getResultChecksum() { return resultChecksum; }
    public String getStatus() { return status; }
    public Boolean getAutoRecalcForecast() { return autoRecalcForecast; }
    public Boolean getAutoRecalcInventory() { return autoRecalcInventory; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public void setFactoryCode(String factoryCode) { this.factoryCode = factoryCode; }
    public void setForecastVersionId(Long forecastVersionId) { this.forecastVersionId = forecastVersionId; }
    public void setInventorySnapshotId(Long inventorySnapshotId) { this.inventorySnapshotId = inventorySnapshotId; }
    public void setShipmentBatchId(Long shipmentBatchId) { this.shipmentBatchId = shipmentBatchId; }
    public void setCapacityVersionId(Long capacityVersionId) { this.capacityVersionId = capacityVersionId; }
    public void setRuleVersion(String ruleVersion) { this.ruleVersion = ruleVersion; }
    public void setCurrentWeekStart(LocalDate currentWeekStart) { this.currentWeekStart = currentWeekStart; }
    public void setInputChecksum(String inputChecksum) { this.inputChecksum = inputChecksum; }
    public void setResultChecksum(String resultChecksum) { this.resultChecksum = resultChecksum; }
    public void setStatus(String status) { this.status = status; }
    public void setAutoRecalcForecast(Boolean autoRecalcForecast) { this.autoRecalcForecast = autoRecalcForecast; }
    public void setAutoRecalcInventory(Boolean autoRecalcInventory) { this.autoRecalcInventory = autoRecalcInventory; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
