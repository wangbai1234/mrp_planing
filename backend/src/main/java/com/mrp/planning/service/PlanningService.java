package com.mrp.planning.service;

import com.mrp.capacity.domain.CapacityLine;
import com.mrp.capacity.domain.CapacityVersion;
import com.mrp.capacity.repository.CapacityMapper;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.domain.ForecastVersion;
import com.mrp.forecast.repository.ForecastMapper;
import com.mrp.inventory.domain.InventoryDetail;
import com.mrp.inventory.repository.InventoryMapper;
import com.mrp.planning.domain.*;
import com.mrp.planning.repository.PlanMapper;
import com.mrp.shipment.domain.ShipmentDetail;
import com.mrp.shipment.repository.ShipmentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlanningService {

    private static final Logger log = LoggerFactory.getLogger(PlanningService.class);
    private static final String RULE_VERSION = "v1";

    private final PlanMapper planMapper;
    private final ForecastMapper forecastMapper;
    private final InventoryMapper inventoryMapper;
    private final ShipmentMapper shipmentMapper;
    private final CapacityMapper capacityMapper;

    public PlanningService(PlanMapper planMapper, ForecastMapper forecastMapper,
                          InventoryMapper inventoryMapper, ShipmentMapper shipmentMapper,
                          CapacityMapper capacityMapper) {
        this.planMapper = planMapper;
        this.forecastMapper = forecastMapper;
        this.inventoryMapper = inventoryMapper;
        this.shipmentMapper = shipmentMapper;
        this.capacityMapper = capacityMapper;
    }

    @Transactional
    public PlanVersion recalculate(Long forecastVersionId, Long inventorySnapshotId,
                                   Long shipmentBatchId, Long capacityVersionId,
                                   String factoryCode, LocalDate currentWeekStart,
                                   Long userId) {
        // Auto-detect latest active capacity version if not provided
        if (capacityVersionId == null) {
            CapacityVersion activeCapVersion = capacityMapper.selectActiveVersion();
            if (activeCapVersion != null) {
                capacityVersionId = activeCapVersion.id();
                log.info("Auto-detected active capacity version: {}", capacityVersionId);
            }
        }

        // 1. Compute input checksum
        String inputChecksum = PlanningCalculator.computeInputChecksum(
                forecastVersionId, inventorySnapshotId, shipmentBatchId,
                capacityVersionId, RULE_VERSION, currentWeekStart);

        // 2. Create plan version (CALCULATING)
        PlanVersion latest = planMapper.selectLatestVersion(factoryCode);
        int nextVersionNo = (latest != null) ? latest.versionNo() + 1 : 1;

        PlanVersion version = new PlanVersion(
                null, nextVersionNo, factoryCode,
                forecastVersionId, inventorySnapshotId, shipmentBatchId, capacityVersionId,
                RULE_VERSION, currentWeekStart, inputChecksum, null,
                PlanVersion.STATUS_CALCULATING, false, false, userId, null, null
        );
        planMapper.insertVersion(version);

        try {
            // 3. Read inputs
            List<ForecastDetail> forecasts = forecastMapper.selectDetailsByVersionId(forecastVersionId)
                    .stream()
                    .filter(f -> factoryCode.equals(f.factoryCode()))
                    .toList();
            List<InventoryDetail> inventories = inventorySnapshotId != null
                    ? inventoryMapper.selectEffectiveDetailsBySnapshotId(inventorySnapshotId)
                    : List.of();
            List<ShipmentDetail> shipments = shipmentBatchId != null
                    ? shipmentMapper.selectDetailsByBatchId(shipmentBatchId)
                    : List.of();
            List<CapacityLine> capacityLines = capacityVersionId != null
                    ? capacityMapper.selectActiveLinesByFactory(capacityVersionId, factoryCode)
                    : List.of();

            // 4. Aggregate by material
            Map<String, BigDecimal> forecastByMaterial = forecasts.stream()
                    .filter(f -> f.forecastQty() != null)
                    .collect(Collectors.groupingBy(ForecastDetail::materialId, 
                            Collectors.reducing(BigDecimal.ZERO, ForecastDetail::forecastQty, BigDecimal::add)));

            Map<String, Long> inventoryByMaterial = inventories.stream()
                    .filter(i -> factoryCode.equals(i.factoryCode()))
                    .collect(Collectors.groupingBy(InventoryDetail::materialId, Collectors.summingLong(InventoryDetail::quantity)));

            Map<String, Long> shipmentByMaterial = shipments.stream()
                    .collect(Collectors.groupingBy(ShipmentDetail::materialId, Collectors.summingLong(ShipmentDetail::shippedQty)));

            long totalWeeklyCapacity = capacityLines.stream()
                    .mapToLong(CapacityLine::weeklyCapacity)
                    .sum();

            // 5. Calculate for each material
            List<PlanDetail> allDetails = new ArrayList<>();
            Set<String> allMaterials = new HashSet<>(forecastByMaterial.keySet());

            for (String materialId : allMaterials) {
                // Get source months (up to 6 from forecast)
                List<YearMonth> sourceMonths = forecasts.stream()
                        .filter(f -> materialId.equals(f.materialId()))
                        .map(f -> YearMonth.from(f.planMonth()))
                        .distinct()
                        .sorted()
                        .limit(6)
                        .toList();

                // Build per-month forecasts
                long inventory = inventoryByMaterial.getOrDefault(materialId, 0L);
                long shipped = shipmentByMaterial.getOrDefault(materialId, 0L);

                List<PlanningCalculator.MonthlyForecast> monthlyForecasts = new ArrayList<>();
                for (YearMonth ym : sourceMonths) {
                    long monthForecast = forecasts.stream()
                            .filter(f -> materialId.equals(f.materialId()) && YearMonth.from(f.planMonth()).equals(ym))
                            .map(f -> f.forecastQty() != null ? f.forecastQty() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .longValue();
                    // Use inventory and shipped for the first month only (as per business rule)
                    long monthInventory = ym.equals(sourceMonths.get(0)) ? inventory : 0;
                    long monthShipped = ym.equals(sourceMonths.get(0)) ? shipped : 0;
                    monthlyForecasts.add(PlanningCalculator.MonthlyForecast.of(ym, monthForecast, monthInventory, monthShipped));
                }

                // Generate 12-week plan
                List<WeekPlan> weekPlans = PlanningCalculator.generate12WeekPlan(
                        monthlyForecasts, currentWeekStart, null, null, null);

                // Check capacity
                if (totalWeeklyCapacity > 0) {
                    weekPlans = PlanningCalculator.checkCapacity(weekPlans, totalWeeklyCapacity);
                }

                // Get material metadata
                ForecastDetail meta = forecasts.stream()
                        .filter(f -> materialId.equals(f.materialId()))
                        .findFirst().orElse(null);

                // Convert to PlanDetail
                for (WeekPlan wp : weekPlans) {
                    allDetails.add(new PlanDetail(
                            null, version.id(), factoryCode, materialId,
                            meta != null ? meta.materialName() : null,
                            null, null,
                            wp.weekStartDate(), wp.physicalMonth(), wp.sourceMonth(),
                            wp.slot(), wp.isCarry(), wp.isLocked(),
                            wp.systemQuantity(), wp.manualQuantity(), wp.effectiveQuantity(),
                            wp.capacityExceeded(), wp.capacityExcessQty()
                    ));
                }
            }

            // 6. Batch write
            for (int i = 0; i < allDetails.size(); i += 500) {
                List<PlanDetail> batch = allDetails.subList(i, Math.min(i + 500, allDetails.size()));
                planMapper.insertDetails(batch);
            }

            // 7. Compute result checksum
            String resultChecksum = PlanningCalculator.computeResultChecksum(
                    allDetails.stream()
                            .map(d -> new WeekPlan(d.weekStartDate(), d.physicalMonth(), d.sourceMonth(),
                                    d.slot(), d.isCarry(), d.isLocked(), d.systemQuantity(),
                                    d.manualQuantity(), d.effectiveQuantity(), d.capacityExceeded(), d.capacityExcessQty()))
                            .toList()
            );

            // 8. Mark READY
            planMapper.updateVersionStatus(version.id(), PlanVersion.STATUS_READY, resultChecksum, 0);

            log.info("Recalculation complete: version={}, details={}, checksum={}",
                    version.id(), allDetails.size(), resultChecksum);

            return planMapper.selectVersionById(version.id());

        } catch (Exception e) {
            log.error("Recalculation failed for version {}", version.id(), e);
            planMapper.updateVersionStatus(version.id(), PlanVersion.STATUS_FAILED, null, 0);
            throw e;
        }
    }

    public PlanVersion getLatestPlan(String factoryCode) {
        return planMapper.selectLatestVersion(factoryCode);
    }

    public List<PlanVersion> getAllLatestPlans() {
        return planMapper.selectAllLatestVersions();
    }

    @Transactional
    public List<PlanVersion> recalculateAllFactories(Long forecastVersionId, Long inventorySnapshotId,
                                                      Long shipmentBatchId, Long capacityVersionId,
                                                      LocalDate currentWeekStart, Long userId) {
        // Get all factories from forecast
        List<String> factories = forecastMapper.selectDistinctFactoriesByVersionId(forecastVersionId);
        if (factories.isEmpty()) {
            throw new com.mrp.common.exception.BusinessException("NO_FORECAST", "经营计划数据为空");
        }

        // Validate factory values
        List<String> validFactories = List.of("永惠", "爱培科");
        List<String> invalidFactories = factories.stream()
                .filter(f -> !validFactories.contains(f))
                .toList();
        if (!invalidFactories.isEmpty()) {
            throw new com.mrp.common.exception.BusinessException("INVALID_FACTORY",
                    "经营计划中存在无效的工厂值: " + String.join(", ", invalidFactories) + "。请修改Excel中的工厂列为「永惠」或「爱培科」后重新导入。");
        }

        List<PlanVersion> versions = new ArrayList<>();
        for (String factoryCode : factories) {
            log.info("Recalculating for factory: {}", factoryCode);
            PlanVersion version = recalculate(forecastVersionId, inventorySnapshotId,
                    shipmentBatchId, capacityVersionId, factoryCode, currentWeekStart, userId);
            versions.add(version);
        }
        return versions;
    }

    public List<PlanDetail> getPlanDetails(Long versionId) {
        return planMapper.selectDetailsByVersionId(versionId);
    }

    public List<PlanVersion> listVersions(String factoryCode) {
        return planMapper.selectVersionsByFactory(factoryCode);
    }

    public Long getLatestForecastVersionId() {
        ForecastVersion version = forecastMapper.selectLatestVersion();
        return version != null ? version.id() : null;
    }
}
