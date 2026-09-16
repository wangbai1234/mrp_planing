package com.mrp.planning.service;

import com.mrp.bom.domain.BomExplosionResult;
import com.mrp.bom.service.BomExplosionService;
import com.mrp.bom.service.BomExplosionService.BomGraph;
import com.mrp.capacity.domain.CapacityLine;
import com.mrp.capacity.domain.CapacityVersion;
import com.mrp.capacity.repository.CapacityMapper;
import com.mrp.common.exception.BusinessException;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.domain.ForecastVersion;
import com.mrp.forecast.repository.ForecastMapper;
import com.mrp.inventory.domain.InventoryDetail;
import com.mrp.inventory.repository.InventoryMapper;
import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.domain.MaterialCategory;
import com.mrp.masterdata.repository.MaterialCategoryMapper;
import com.mrp.masterdata.repository.MaterialMapper;
import com.mrp.planning.domain.*;
import com.mrp.planning.repository.PlanMapper;
import com.mrp.shipment.domain.ShipmentDetail;
import com.mrp.shipment.repository.ShipmentMapper;
import com.mrp.task.repository.CalcTaskMapper;
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
    private final BomExplosionService bomExplosionService;
    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper categoryMapper;
    private final CalcTaskMapper calcTaskMapper;

    public PlanningService(PlanMapper planMapper, ForecastMapper forecastMapper,
                          InventoryMapper inventoryMapper, ShipmentMapper shipmentMapper,
                          CapacityMapper capacityMapper, BomExplosionService bomExplosionService,
                          MaterialMapper materialMapper, MaterialCategoryMapper categoryMapper,
                          CalcTaskMapper calcTaskMapper) {
        this.planMapper = planMapper;
        this.forecastMapper = forecastMapper;
        this.inventoryMapper = inventoryMapper;
        this.shipmentMapper = shipmentMapper;
        this.capacityMapper = capacityMapper;
        this.bomExplosionService = bomExplosionService;
        this.materialMapper = materialMapper;
        this.categoryMapper = categoryMapper;
        this.calcTaskMapper = calcTaskMapper;
    }

    @Transactional
    public PlanVersion recalculate(Long forecastVersionId, Long inventorySnapshotId,
                                   Long shipmentBatchId, Long capacityVersionId,
                                   String factoryCode, LocalDate currentWeekStart,
                                   Long userId) {
        if (capacityVersionId == null) {
            CapacityVersion activeCapVersion = capacityMapper.selectActiveVersion();
            if (activeCapVersion != null) {
                capacityVersionId = activeCapVersion.id();
                log.info("Auto-detected active capacity version: {}", capacityVersionId);
            }
        }

        String inputChecksum = PlanningCalculator.computeInputChecksum(
                forecastVersionId, inventorySnapshotId, shipmentBatchId,
                capacityVersionId, RULE_VERSION, currentWeekStart);

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

            List<PlanDetail> allDetails = new ArrayList<>();
            Set<String> allMaterials = new HashSet<>(forecastByMaterial.keySet());

            for (String materialId : allMaterials) {
                List<YearMonth> sourceMonths = forecasts.stream()
                        .filter(f -> materialId.equals(f.materialId()))
                        .map(f -> YearMonth.from(f.planMonth()))
                        .distinct()
                        .sorted()
                        .limit(6)
                        .toList();

                long inventory = inventoryByMaterial.getOrDefault(materialId, 0L);
                long shipped = shipmentByMaterial.getOrDefault(materialId, 0L);

                List<PlanningCalculator.MonthlyForecast> monthlyForecasts = new ArrayList<>();
                for (YearMonth ym : sourceMonths) {
                    long monthForecast = forecasts.stream()
                            .filter(f -> materialId.equals(f.materialId()) && YearMonth.from(f.planMonth()).equals(ym))
                            .map(f -> f.forecastQty() != null ? f.forecastQty() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .longValue();
                    long monthInventory = ym.equals(sourceMonths.get(0)) ? inventory : 0;
                    long monthShipped = ym.equals(sourceMonths.get(0)) ? shipped : 0;
                    monthlyForecasts.add(PlanningCalculator.MonthlyForecast.of(ym, monthForecast, monthInventory, monthShipped));
                }

                List<WeekPlan> weekPlans = PlanningCalculator.generate12WeekPlan(
                        monthlyForecasts, currentWeekStart, null, null, null);

                if (totalWeeklyCapacity > 0) {
                    weekPlans = PlanningCalculator.checkCapacity(weekPlans, totalWeeklyCapacity);
                }

                ForecastDetail meta = forecasts.stream()
                        .filter(f -> materialId.equals(f.materialId()))
                        .findFirst().orElse(null);

                for (WeekPlan wp : weekPlans) {
                    allDetails.add(new PlanDetail(
                            null, version.id(), factoryCode, materialId,
                            meta != null ? meta.materialName() : null,
                            null, null,
                            wp.weekStartDate(), wp.physicalMonth(), wp.sourceMonth(),
                            wp.slot(), wp.isCarry(), wp.isLocked(),
                            wp.systemQuantity(), wp.manualQuantity(), wp.effectiveQuantity(),
                            wp.capacityExceeded(), wp.capacityExcessQty(),
                            null, null
                    ));
                }
            }

            for (int i = 0; i < allDetails.size(); i += 2000) {
                List<PlanDetail> batch = allDetails.subList(i, Math.min(i + 2000, allDetails.size()));
                planMapper.insertDetails(batch);
            }

            String resultChecksum = PlanningCalculator.computeResultChecksum(
                    allDetails.stream()
                            .map(d -> new WeekPlan(d.weekStartDate(), d.physicalMonth(), d.sourceMonth(),
                                    d.slot(), d.isCarry(), d.isLocked(), d.systemQuantity(),
                                    d.manualQuantity(), d.effectiveQuantity(), d.capacityExceeded(), d.capacityExcessQty()))
                            .toList()
            );

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

    /**
     * 按拆分维度重算排产（优化版）。
     *
     * 事务设计：
     * - 阶段1（无事务）：加载数据、BOM 展开、计算排产
     * - 阶段2（短事务）：写入 plan_version + plan_detail + 更新状态
     *
     * BOM 算法优化：
     * - 一次性加载全部 BOM 到内存（~157k 行）
     * - 一次性加载全部物料和分类
     * - 递归在内存中完成，无 SQL 查询
     * - 同一 BomGraph 被所有整机复用
     */
    public PlanVersion recalculateWithBomExplosion(
            Long forecastVersionId, Long inventorySnapshotId,
            Long shipmentBatchId, Long capacityVersionId,
            String factoryCode, LocalDate currentWeekStart,
            List<String> splitCategories, Long userId,
            Long taskId) {

        long totalStart = System.currentTimeMillis();

        // === 阶段1：校验 + 加载 + 计算（无事务） ===

        // 1. 校验 splitCategories
        if (splitCategories == null || splitCategories.isEmpty()) {
            throw new BusinessException("INVALID_SPLIT", "拆分维度不能为空");
        }
        List<String> uniqueCategories = splitCategories.stream().distinct().toList();

        List<MaterialCategory> categories = categoryMapper.selectByCodes(uniqueCategories);
        Map<String, MaterialCategory> categoryMap = categories.stream()
                .collect(Collectors.toMap(MaterialCategory::getCode, c -> c));
        for (String code : uniqueCategories) {
            if (!categoryMap.containsKey(code)) {
                throw new BusinessException("INVALID_CATEGORY", "目标分类不存在或已停用: " + code);
            }
        }

        String splitCategoryNames = uniqueCategories.stream()
                .map(code -> {
                    MaterialCategory cat = categoryMap.get(code);
                    return code + " " + (cat != null ? cat.getName() : "");
                })
                .collect(Collectors.joining(", "));

        if (capacityVersionId == null) {
            CapacityVersion activeCapVersion = capacityMapper.selectActiveVersion();
            if (activeCapVersion != null) {
                capacityVersionId = activeCapVersion.id();
            }
        }

        String inputChecksum = PlanningCalculator.computeInputChecksum(
                forecastVersionId, inventorySnapshotId, shipmentBatchId,
                capacityVersionId, RULE_VERSION, currentWeekStart);

        // 更新进度：加载数据
        updateTaskProgress(taskId, 0, 0, "LOADING", 0, 0);

        // 2. 读取输入
        long loadStart = System.currentTimeMillis();
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

        long totalWeeklyCapacity = capacityLines.stream()
                .mapToLong(CapacityLine::weeklyCapacity)
                .sum();

        // 3. 按整机分组
        Map<String, List<ForecastDetail>> forecastsByMaterial = forecasts.stream()
                .filter(f -> f.forecastQty() != null && f.forecastQty().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(ForecastDetail::materialId));

        Map<String, Long> inventoryByMaterial = inventories.stream()
                .filter(i -> factoryCode.equals(i.factoryCode()))
                .collect(Collectors.groupingBy(InventoryDetail::materialId, Collectors.summingLong(InventoryDetail::quantity)));

        Map<String, Long> shipmentByMaterial = shipments.stream()
                .collect(Collectors.groupingBy(ShipmentDetail::materialId, Collectors.summingLong(ShipmentDetail::shippedQty)));

        long loadEnd = System.currentTimeMillis();
        log.info("[perf] 数据加载耗时: {}ms, 整机数: {}", loadEnd - loadStart, forecastsByMaterial.size());

        // 更新进度：加载 BOM 图
        updateTaskProgress(taskId, 0, forecastsByMaterial.size(), "EXPLODING_BOM", 0, 0);

        // 4. 加载 BOM 子图（只加载整机可达的节点，避免全量加载）
        long bomLoadStart = System.currentTimeMillis();
        Set<String> rootMaterialCodes = forecastsByMaterial.keySet();
        BomGraph bomGraph;
        if (rootMaterialCodes.size() <= 100) {
            // 整机数量较少时，使用子图加载（BFS 找可达节点）
            bomGraph = bomExplosionService.loadBomSubgraph(rootMaterialCodes);
        } else {
            // 整机数量较多时，使用全量加载（避免多次 SQL）
            bomGraph = bomExplosionService.loadBomGraph();
        }
        long bomLoadEnd = System.currentTimeMillis();
        log.info("[perf] BOM 图加载耗时: {}ms, 整机数: {}", bomLoadEnd - bomLoadStart, rootMaterialCodes.size());

        // 5. BOM 展开（全在内存中）
        long explodeStart = System.currentTimeMillis();
        Map<String, BomExplosionResult> explosionCache = new HashMap<>();
        List<String> failedMaterials = new ArrayList<>();
        int successRoots = 0;
        int failedRoots = 0;
        int processed = 0;

        for (String materialId : rootMaterialCodes) {
            Material material = bomGraph.materialByCode().get(materialId);
            if (material == null) {
                log.warn("整机物料不存在: {}", materialId);
                failedMaterials.add(materialId + " (物料不存在)");
                failedRoots++;
                processed++;
                continue;
            }

            BomExplosionResult result = bomExplosionService.explode(
                    material.id(), BigDecimal.ONE, uniqueCategories, bomGraph);
            explosionCache.put(materialId, result);

            if (result.getSchedulingNodes().isEmpty()) {
                String reason = result.getUnmatchedLeaves().isEmpty()
                        ? "无BOM子项"
                        : "未找到目标拆分分类";
                failedMaterials.add(materialId + " - " + material.materialName() + " (" + reason + ")");
                failedRoots++;
            } else {
                successRoots++;
            }

            processed++;
            // 每 10 个整机更新一次进度
            if (processed % 10 == 0 || processed == rootMaterialCodes.size()) {
                updateTaskProgress(taskId, processed, rootMaterialCodes.size(), "EXPLODING_BOM", successRoots, failedRoots);
            }
        }

        long explodeEnd = System.currentTimeMillis();
        log.info("[perf] BOM 展开耗时: {}ms, 成功: {}, 失败: {}", explodeEnd - explodeStart, successRoots, failedRoots);

        // 6. 如果有任何整机失败，整个批次失败
        if (!failedMaterials.isEmpty()) {
            String errorDetail = String.join("\n", failedMaterials);
            throw new BusinessException("BOM_EXPLOSION_FAILED",
                    "排产生成失败，以下整机无法按拆分维度展开:\n" + errorDetail);
        }

        // 7. 构建虚拟预测
        long calcStart = System.currentTimeMillis();
        record VirtualForecast(String rootMaterialCode, String rootMaterialName,
                               String materialCode, String materialName,
                               YearMonth month, BigDecimal quantity) {}

        List<VirtualForecast> virtualForecasts = new ArrayList<>();

        for (var entry : forecastsByMaterial.entrySet()) {
            String materialId = entry.getKey();
            List<ForecastDetail> materialForecasts = entry.getValue();
            BomExplosionResult result = explosionCache.get(materialId);

            // 从 bomGraph 获取根物料名称（已缓存，无 SQL）
            Material rootMat = bomGraph.materialByCode().get(materialId);
            String rootName = rootMat != null ? rootMat.materialName() : "";

            for (BomExplosionResult.SchedulingNode node : result.getSchedulingNodes()) {
                for (ForecastDetail fd : materialForecasts) {
                    YearMonth ym = YearMonth.from(fd.planMonth());
                    BigDecimal qty = fd.forecastQty().multiply(node.getQuantityMultiplier());
                    virtualForecasts.add(new VirtualForecast(
                            materialId, rootName,
                            node.getMaterialCode(), node.getMaterialName(),
                            ym, qty));
                }
            }
        }

        // 8. 聚合
        record AggKey(String rootCode, String rootName, String matCode, String matName) {}
        Map<AggKey, Map<YearMonth, BigDecimal>> aggregated = new LinkedHashMap<>();
        for (VirtualForecast vf : virtualForecasts) {
            AggKey key = new AggKey(vf.rootMaterialCode, vf.rootMaterialName,
                    vf.materialCode, vf.materialName);
            aggregated.computeIfAbsent(key, k -> new LinkedHashMap<>())
                    .merge(vf.month, vf.quantity, BigDecimal::add);
        }

        // 9. 生成 12 周排产
        List<PlanDetail> allDetails = new ArrayList<>();

        for (var entry : aggregated.entrySet()) {
            AggKey key = entry.getKey();
            Map<YearMonth, BigDecimal> monthQty = entry.getValue();

            List<PlanningCalculator.MonthlyForecast> monthlyForecasts = new ArrayList<>();
            List<YearMonth> sortedMonths = monthQty.keySet().stream().sorted().toList();

            for (int i = 0; i < sortedMonths.size(); i++) {
                YearMonth ym = sortedMonths.get(i);
                long forecast = monthQty.get(ym).longValue();
                long inv = i == 0 ? inventoryByMaterial.getOrDefault(key.rootCode, 0L) : 0;
                long ship = i == 0 ? shipmentByMaterial.getOrDefault(key.rootCode, 0L) : 0;
                monthlyForecasts.add(PlanningCalculator.MonthlyForecast.of(ym, forecast, inv, ship));
            }

            List<WeekPlan> weekPlans = PlanningCalculator.generate12WeekPlan(
                    monthlyForecasts, currentWeekStart, null, null, null);

            if (totalWeeklyCapacity > 0) {
                weekPlans = PlanningCalculator.checkCapacity(weekPlans, totalWeeklyCapacity);
            }

            for (WeekPlan wp : weekPlans) {
                allDetails.add(new PlanDetail(
                        null, 0L, factoryCode,
                        key.matCode, key.matName,
                        null, null,
                        wp.weekStartDate(), wp.physicalMonth(), wp.sourceMonth(),
                        wp.slot(), wp.isCarry(), wp.isLocked(),
                        wp.systemQuantity(), wp.manualQuantity(), wp.effectiveQuantity(),
                        wp.capacityExceeded(), wp.capacityExcessQty(),
                        key.rootCode, key.rootName
                ));
            }
        }

        long calcEnd = System.currentTimeMillis();
        log.info("[perf] 排产计算耗时: {}ms, 生成 plan_detail 行数: {}", calcEnd - calcStart, allDetails.size());

        // 更新进度：写入
        updateTaskProgress(taskId, rootMaterialCodes.size(), rootMaterialCodes.size(), "WRITING", successRoots, failedRoots);

        // === 阶段2：短事务写入 ===
        return writePlanVersion(factoryCode, forecastVersionId, inventorySnapshotId,
                shipmentBatchId, capacityVersionId, currentWeekStart, inputChecksum,
                uniqueCategories, splitCategoryNames, userId, allDetails, totalStart);
    }

    /**
     * 短事务：写入 plan_version + plan_detail + 更新状态。
     */
    @Transactional
    public PlanVersion writePlanVersion(String factoryCode, Long forecastVersionId,
                                         Long inventorySnapshotId, Long shipmentBatchId,
                                         Long capacityVersionId, LocalDate currentWeekStart,
                                         String inputChecksum, List<String> uniqueCategories,
                                         String splitCategoryNames, Long userId,
                                         List<PlanDetail> allDetails, long totalStart) {
        // 创建 plan_version
        PlanVersion latest = planMapper.selectLatestVersion(factoryCode);
        int nextVersionNo = (latest != null) ? latest.versionNo() + 1 : 1;

        String categoriesJson = "[" + uniqueCategories.stream()
                .map(c -> "\"" + c + "\"")
                .collect(Collectors.joining(",")) + "]";
        PlanVersion version = new PlanVersion(
                null, nextVersionNo, factoryCode,
                forecastVersionId, inventorySnapshotId, shipmentBatchId, capacityVersionId,
                RULE_VERSION, currentWeekStart, inputChecksum, null,
                PlanVersion.STATUS_CALCULATING, false, false,
                categoriesJson, splitCategoryNames, userId, null, null
        );
        planMapper.insertVersion(version);

        // 设置 planVersionId
        for (PlanDetail d : allDetails) {
            // 使用反射或直接设置（PlanDetail 是 record，需要创建新实例）
        }
        // PlanDetail 是 record，需要重新创建带 planVersionId 的实例
        List<PlanDetail> detailsWithVersionId = allDetails.stream()
                .map(d -> new PlanDetail(
                        d.id(), version.id(), d.factoryCode(),
                        d.materialId(), d.materialName(),
                        d.model(), d.meMaterialId(),
                        d.weekStartDate(), d.physicalMonth(), d.sourceMonth(),
                        d.slot(), d.isCarry(), d.isLocked(),
                        d.systemQuantity(), d.manualQuantity(), d.effectiveQuantity(),
                        d.capacityExceeded(), d.capacityExcessQty(),
                        d.rootMaterialCode(), d.rootMaterialName()
                ))
                .toList();

        // 批量写入
        long writeStart = System.currentTimeMillis();
        for (int i = 0; i < detailsWithVersionId.size(); i += 2000) {
            List<PlanDetail> batch = detailsWithVersionId.subList(i, Math.min(i + 2000, detailsWithVersionId.size()));
            planMapper.insertDetails(batch);
        }
        long writeEnd = System.currentTimeMillis();
        log.info("[perf] 数据写入耗时: {}ms", writeEnd - writeStart);

        // 计算 checksum
        String resultChecksum = PlanningCalculator.computeResultChecksum(
                detailsWithVersionId.stream()
                        .map(d -> new WeekPlan(d.weekStartDate(), d.physicalMonth(), d.sourceMonth(),
                                d.slot(), d.isCarry(), d.isLocked(), d.systemQuantity(),
                                d.manualQuantity(), d.effectiveQuantity(), d.capacityExceeded(), d.capacityExcessQty()))
                        .toList()
        );

        // 标记成功
        planMapper.updateVersionStatus(version.id(), PlanVersion.STATUS_READY, resultChecksum, 0);

        long totalEnd = System.currentTimeMillis();
        log.info("[perf] 总耗时: {}ms, 版本: {}, 详情行数: {}", totalEnd - totalStart, version.id(), detailsWithVersionId.size());

        return planMapper.selectVersionById(version.id());
    }

    /**
     * 更新任务进度（静默失败，不影响主流程）
     */
    private void updateTaskProgress(Long taskId, int current, int total, String phase,
                                     int successRoots, int failedRoots) {
        if (taskId == null) return;
        try {
            calcTaskMapper.updateProgress(taskId, current, total, phase, successRoots, failedRoots);
        } catch (Exception e) {
            log.warn("更新任务进度失败: taskId={}, error={}", taskId, e.getMessage());
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
        List<String> factories = forecastMapper.selectDistinctFactoriesByVersionId(forecastVersionId);
        if (factories.isEmpty()) {
            throw new com.mrp.common.exception.BusinessException("NO_FORECAST", "经营计划数据为空");
        }

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

    public List<String> getDistinctFactories(Long forecastVersionId) {
        return forecastMapper.selectDistinctFactoriesByVersionId(forecastVersionId);
    }
}
