package com.mrp.mrp.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.domain.*;
import com.mrp.mrp.engine.DailyDistributionEngine;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.mrp.repository.MrpCalcRunMapper;
import com.mrp.mrp.repository.MrpDailyPlanDetailMapper;
import com.mrp.mrp.repository.ProductionCalendarDayMapper;
import com.mrp.mrp.service.DailyPlanningService;
import com.mrp.mrp.service.ProductionCapacityRules;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/mrp")
public class MrpDailyPlanningController {

    private final com.mrp.mrp.service.DailyCapacityService capacityService;
    private final DailyPlanningService dailyPlanningService;
    private final MrpDailyPlanDetailMapper dailyPlanMapper;
    private final MrpCalcBatchMapper batchMapper;
    private final MrpCalcRunMapper runMapper;
    private final ProductionCalendarDayMapper calendarDayMapper;
    private final com.mrp.mrp.repository.RollingPlanningMapper rollingMapper;

    public MrpDailyPlanningController(DailyPlanningService dailyPlanningService,
                                       MrpDailyPlanDetailMapper dailyPlanMapper,
                                       MrpCalcBatchMapper batchMapper,
                                       MrpCalcRunMapper runMapper,
                                       ProductionCalendarDayMapper calendarDayMapper, com.mrp.mrp.service.DailyCapacityService capacityService,
                                       com.mrp.mrp.repository.RollingPlanningMapper rollingMapper) {
        this.rollingMapper = rollingMapper;
        this.capacityService = capacityService;
        this.dailyPlanningService = dailyPlanningService;
        this.dailyPlanMapper = dailyPlanMapper;
        this.batchMapper = batchMapper;
        this.runMapper = runMapper;
        this.calendarDayMapper = calendarDayMapper;
    }

    private void requireScheduleView() {
        if (!CurrentUser.hasPermission("mrp_workspace:view"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看整机排产");
    }

    private void requireSummaryView(String view) {
        if ("TERMINAL".equals(view)) {
            if (!CurrentUser.hasPermission("mrp_shortage:view"))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看物料明细");
        } else requireScheduleView();
    }

    @PostMapping("/batches/{batchId}/distribute")
    public ResponseEntity<ApiResponse<DailyDistributionEngine.DistributionResult>> distribute(
            @PathVariable Long batchId,
            @RequestParam Long runId,
            @RequestParam Long calendarVersionId,
            @RequestParam Long materialPolicyVersionId) {
        DailyDistributionEngine.DistributionResult result = dailyPlanningService.distributeFrozen(batchId, runId,
                calendarVersionId, materialPolicyVersionId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/batches/{batchId}/daily-plans")
    public ResponseEntity<ApiResponse<List<MrpDailyPlanDetail>>> getDailyPlans(
            @PathVariable Long batchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        requireScheduleView();
        List<MrpDailyPlanDetail> plans;
        if (startDate != null && endDate != null) {
            plans = dailyPlanningService.getDailyPlans(batchId, startDate, endDate);
        } else {
            plans = dailyPlanningService.getDailyPlans(batchId);
        }
        return ResponseEntity.ok(ApiResponse.ok(plans));
    }

    @GetMapping("/batches/{batchId}/plans/weekly")
    public ResponseEntity<ApiResponse<List<DailyPlanningService.WeeklySummaryRow>>> getWeeklySummary(
            @PathVariable Long batchId, @RequestParam(required = false) String view) {
        requireSummaryView(view);
        List<DailyPlanningService.WeeklySummaryRow> summary = dailyPlanningService.getWeeklySummary(batchId, view);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/batches/{batchId}/plans/monthly")
    public ResponseEntity<ApiResponse<List<DailyPlanningService.MonthlySummaryRow>>> getMonthlySummary(
            @PathVariable Long batchId, @RequestParam(required = false) String view) {
        requireSummaryView(view);
        List<DailyPlanningService.MonthlySummaryRow> summary = dailyPlanningService.getMonthlySummary(batchId, view);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/batches/{batchId}/plans/conservation")
    public ResponseEntity<ApiResponse<DailyPlanningService.ConservationCheckResult>> getConservation(
            @PathVariable Long batchId) {
        requireScheduleView();
        DailyPlanningService.ConservationCheckResult result = dailyPlanningService.getSourceMonthConservation(batchId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/plans/{planId}/daily-grid")
    public ResponseEntity<ApiResponse<DailyGridResponse>> getDailyGrid(
            @PathVariable Long planId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String factoryCode,
            @RequestParam(required = false) String rootMaterialCode,
            @RequestParam(required = false) String terminalMaterialCode,
            @RequestParam(required = false) String terminalType,
            @RequestParam(required = false) String supplyMode,
            @RequestParam(required = false) String view,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页参数不合法");
        MrpCalcBatch batch = batchMapper.selectById(planId);
        if (batch == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("BATCH_NOT_FOUND", "Batch not found: " + planId));
        }
        String effectiveView = ProductionCapacityRules.view(batch, view);
        if ("TERMINAL".equals(effectiveView) && !CurrentUser.hasPermission("mrp_shortage:view"))
            return ResponseEntity.status(403).body(ApiResponse.error("FORBIDDEN", "无权查看物料明细"));
        if ("MACHINE_OUTPUT".equals(effectiveView) && !CurrentUser.hasPermission("mrp_workspace:view"))
            return ResponseEntity.status(403).body(ApiResponse.error("FORBIDDEN", "无权查看整机排产"));
        Long runId = batch.getActiveRunId();
        if (runId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("NO_ACTIVE_RUN", "No active run for this batch"));
        }

        List<MrpDailyPlanDetail> runPlans = dailyPlanMapper.selectByRunId(runId);
        LocalDate effectiveStart = startDate;
        LocalDate effectiveEnd = endDate;
        if (effectiveStart == null || effectiveEnd == null) {
            Optional<LocalDate> minPlanDate = runPlans.stream().map(MrpDailyPlanDetail::getScheduleDate).min(LocalDate::compareTo);
            effectiveStart = effectiveStart != null ? effectiveStart : minPlanDate.orElse(LocalDate.now());
            effectiveEnd = effectiveEnd != null ? effectiveEnd : effectiveStart.plusDays(27);
        }
        if (effectiveEnd.isBefore(effectiveStart) || java.time.temporal.ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) > 365) {
            return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_DATE_RANGE", "End date must not be before start date"));
        }

        List<MrpDailyPlanDetail> allPlans = dailyPlanningService.applyOverrides(planId, runPlans);
        final LocalDate filterStart = effectiveStart;
        final LocalDate filterEnd = effectiveEnd;
        Map<String, List<FactoryWarehouseMappingDetail>> warehouseByFactory = batchMapper
                .selectFactoryWarehousesByBatchId(planId)
                .stream()
                .collect(Collectors.groupingBy(
                        FactoryWarehouseMappingDetail::getFactoryCode,
                        LinkedHashMap::new, Collectors.toList()));

        Map<String, String> frozenUnits = capacityService.units(batch.getProductionCalendarVersionId());
        List<MrpDailyPlanDetail> filtered = allPlans.stream()
                .filter(p -> ProductionCapacityRules.matches(effectiveView, p))
                .filter(p -> !p.getScheduleDate().isBefore(filterStart))
                .filter(p -> !p.getScheduleDate().isAfter(filterEnd))
                .filter(p -> factoryCode == null || factoryCode.equals(p.getFactoryCode()))
                .filter(p -> rootMaterialCode == null || rootMaterialCode.equals(p.getRootMaterialCode()))
                .filter(p -> terminalMaterialCode == null || terminalMaterialCode.equals(p.getTerminalMaterialCode()))
                .filter(p -> terminalType == null || terminalType.equals(p.getTerminalType()))
                .filter(p -> supplyMode == null || supplyMode.equals(p.getSupplyMode()))
                .collect(Collectors.toList());

        List<LocalDate> calendarDays = filterStart.datesUntil(filterEnd.plusDays(1)).collect(Collectors.toList());
        Map<LocalDate, ProductionCalendarDay> calendarConfig = batch.getProductionCalendarVersionId() == null
                ? Collections.emptyMap()
                : calendarDayMapper.selectByFactoryAndDateRange(batch.getProductionCalendarVersionId(),
                        batch.getFactoryCode(), filterStart, filterEnd).stream()
                    .collect(Collectors.toMap(ProductionCalendarDay::getCalendarDate, day -> day, (a, b) -> a, LinkedHashMap::new));

        List<BusinessWeekDetail> businessWeeks = dailyPlanningService.getBusinessWeeks(planId, filterStart, filterEnd);
        List<WeekGroup> weekGroups = new ArrayList<>();
        for (BusinessWeekDetail businessWeek : businessWeeks) {
            LocalDate visibleStart = businessWeek.getStartDate().isBefore(filterStart)
                    ? filterStart : businessWeek.getStartDate();
            LocalDate visibleEnd = businessWeek.getEndDate().isAfter(filterEnd)
                    ? filterEnd : businessWeek.getEndDate();
            if (!visibleEnd.isBefore(visibleStart)) {
                List<LocalDate> visibleDays = visibleStart.datesUntil(visibleEnd.plusDays(1))
                        .collect(Collectors.toList());
                weekGroups.add(new WeekGroup(
                        dailyPlanningService.businessWeekKey(businessWeek),
                        businessWeek.getStartDate(),
                        businessWeek.getEndDate(),
                        visibleDays));
            }
        }

        Map<String, List<MrpDailyPlanDetail>> rowMap = new LinkedHashMap<>();
        for (MrpDailyPlanDetail plan : filtered) {
            String rowKey = plan.getFactoryCode() + "|" + plan.getRootMaterialCode() + "|"
                    + plan.getTerminalMaterialCode() + "|" + plan.getTerminalType() + "|"
                    + plan.getSupplyMode() + "|" + plan.getPathGroupKey();
            rowMap.computeIfAbsent(rowKey, k -> new ArrayList<>()).add(plan);
        }

        List<GridRow> allRows = new ArrayList<>();
        for (Map.Entry<String, List<MrpDailyPlanDetail>> entry : rowMap.entrySet()) {
            List<MrpDailyPlanDetail> plans = entry.getValue();
            MrpDailyPlanDetail first = plans.get(0);
            List<FactoryWarehouseMappingDetail> warehouses = warehouseByFactory.getOrDefault(first.getFactoryCode(), List.of());
            String warehouseCodes = warehouses.stream().map(FactoryWarehouseMappingDetail::getWarehouseCode)
                    .filter(java.util.Objects::nonNull).distinct().collect(Collectors.joining("、"));
            String warehouseNames = warehouses.stream().map(item -> item.getWarehouseName() == null || item.getWarehouseName().isBlank()
                    ? item.getWarehouseCode() : item.getWarehouseName())
                    .filter(java.util.Objects::nonNull).distinct().collect(Collectors.joining("、"));

            Map<String, String> dayQuantities = new LinkedHashMap<>();
            Map<String, BigDecimal> weekTotals = new LinkedHashMap<>();
            BigDecimal rowTotal = BigDecimal.ZERO;

            for (MrpDailyPlanDetail plan : plans) {
                String dateKey = plan.getScheduleDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
                String qtyStr = plan.getEffectiveQuantity().toPlainString();
                dayQuantities.put(dateKey, qtyStr);

                BusinessWeekDetail businessWeek = dailyPlanningService.findBusinessWeek(
                        businessWeeks, plan.getScheduleDate());
                if (businessWeek != null) {
                    weekTotals.merge(dailyPlanningService.businessWeekKey(businessWeek),
                            plan.getEffectiveQuantity(), BigDecimal::add);
                }
                rowTotal = rowTotal.add(plan.getEffectiveQuantity());
            }

            Map<String, String> weekTotalStrings = new LinkedHashMap<>();
            for (Map.Entry<String, BigDecimal> wt : weekTotals.entrySet()) {
                weekTotalStrings.put(wt.getKey(), wt.getValue().toPlainString());
            }

            allRows.add(new GridRow(
                    entry.getKey(),
                    first.getFactoryCode(),
                    warehouseCodes.isEmpty() ? null : warehouseCodes,
                    warehouseNames.isEmpty() ? null : warehouseNames,
                    first.getRootMaterialCode(),
                    first.getTerminalMaterialCode(),
                    first.getTerminalType(),
                    first.getSupplyMode(),
                    first.getPathGroupKey(),
                    ProductionCapacityRules.MACHINE_OUTPUT.equals(first.getTerminalType()) ? "台" : frozenUnits.get(first.getTerminalMaterialCode()),
                    dayQuantities,
                    weekTotalStrings,
                    rowTotal.toPlainString()
            ));
        }

        int totalRows = allRows.size();
        int fromIndex = Math.min((page - 1) * pageSize, totalRows);
        int toIndex = Math.min(fromIndex + pageSize, totalRows);
        List<GridRow> pageRows = allRows.subList(fromIndex, toIndex);

        DailyGridResponse response = new DailyGridResponse(
                pageRows, totalRows, page, pageSize, calendarDays, weekGroups,
                calendarDays.stream().map(date -> {
                    ProductionCalendarDay config = calendarConfig.get(date);
                    return new CalendarDayInfo(date, config != null && Boolean.TRUE.equals(config.getIsProductionDay()),
                            config != null ? config.getReason() : "生产日历未维护");
                }).collect(Collectors.toList()), capacityService.evaluate(batch, allPlans, effectiveStart, effectiveEnd), runId, effectiveView, ProductionCapacityRules.basis(batch), batch.getRuleVersion());

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/batches/{batchId}/capacity-loads")
    public ResponseEntity<ApiResponse<List<com.mrp.mrp.service.DailyCapacityService.DayLoad>>> capacityLoads(
            @PathVariable Long batchId, @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        requireScheduleView();
        if (endDate.isBefore(startDate) || java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) > 365)
            throw new IllegalArgumentException("日期范围须在366天以内");
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        if (batch == null) throw new IllegalArgumentException("计划不存在");
        List<MrpDailyPlanDetail> plans = ProductionCapacityRules.wholeMachine(batch)
                ? batch.getActiveRunId() == null ? List.of() : rollingMapper.selectMachineLoads(
                        batch.getActiveRunId(), batchId, ProductionCapacityRules.rolling(batch))
                : dailyPlanningService.getDailyPlans(batchId);
        return ResponseEntity.ok(ApiResponse.ok(capacityService.evaluate(batch, plans, startDate, endDate)));
    }

    @PostMapping("/batches/{batchId}/daily-transfers")
    public ResponseEntity<ApiResponse<Void>> transfer(@PathVariable Long batchId, @RequestBody DailyPlanningService.TransferRequest request) {
        dailyPlanningService.transfer(batchId, request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/batches/{batchId}/overrides")
    public ResponseEntity<ApiResponse<Void>> saveOverride(
            @PathVariable Long batchId,
            @RequestBody MrpDailyPlanOverride override) {
        override.setBatchId(batchId);
        override.setOperatorId(CurrentUser.getUserId());
        dailyPlanningService.saveOverride(override);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/overrides/{overrideId}/restore-auto")
    public ResponseEntity<ApiResponse<Void>> restoreAuto(@PathVariable Long overrideId) {
        dailyPlanningService.restoreAuto(overrideId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/batches/{batchId}/overrides")
    public ResponseEntity<ApiResponse<List<MrpDailyPlanOverride>>> getOverrides(@PathVariable Long batchId) {
        List<MrpDailyPlanOverride> overrides = dailyPlanningService.getOverrides(batchId);
        return ResponseEntity.ok(ApiResponse.ok(overrides));
    }

    @GetMapping("/batches/{batchId}/unallocated")
    public ResponseEntity<ApiResponse<List<MrpUnallocatedQuantity>>> getUnallocated(@PathVariable Long batchId) {
        List<MrpUnallocatedQuantity> unallocated = dailyPlanningService.getUnallocated(batchId);
        return ResponseEntity.ok(ApiResponse.ok(unallocated));
    }

    @GetMapping("/batches/{batchId}/unallocated/page")
    public ResponseEntity<ApiResponse<com.mrp.common.response.PageResult<MrpUnallocatedQuantity>>> getPendingUnallocatedPage(
            @PathVariable Long batchId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, @RequestParam(defaultValue = "") String keyword) {
        return ResponseEntity.ok(ApiResponse.ok(dailyPlanningService.getPendingUnallocatedPage(batchId, keyword, page, pageSize)));
    }

    public static class DailyGridResponse {
        public final String view;
        public final String capacityBasis;
        public final String ruleVersion;
        public final Long activeRunId;
        public final List<GridRow> rows;
        public final int totalRows;
        public final int page;
        public final int pageSize;
        public final List<LocalDate> calendarDays;
        public final List<WeekGroup> weekGroups;
        public final List<com.mrp.mrp.service.DailyCapacityService.DayLoad> capacityLoads;
        public final List<CalendarDayInfo> calendarDayInfo;

        public DailyGridResponse(List<GridRow> rows, int totalRows, int page, int pageSize,
                                  List<LocalDate> calendarDays, List<WeekGroup> weekGroups,
                                  List<CalendarDayInfo> calendarDayInfo, List<com.mrp.mrp.service.DailyCapacityService.DayLoad> capacityLoads, Long activeRunId, String view, String capacityBasis, String ruleVersion) {
            this.view = view; this.capacityBasis = capacityBasis; this.ruleVersion = ruleVersion;
            this.activeRunId = activeRunId;
            this.capacityLoads = capacityLoads;
            this.rows = rows;
            this.totalRows = totalRows;
            this.page = page;
            this.pageSize = pageSize;
            this.calendarDays = calendarDays;
            this.weekGroups = weekGroups;
            this.calendarDayInfo = calendarDayInfo;
        }
    }

    public static class CalendarDayInfo {
        public final LocalDate date;
        public final boolean productionDay;
        public final String reason;

        public CalendarDayInfo(LocalDate date, boolean productionDay, String reason) {
            this.date = date;
            this.productionDay = productionDay;
            this.reason = reason;
        }
    }

    public static class GridRow {
        public final String rowKey;
        public final String factoryCode;
        public final String warehouseCode;
        public final String warehouseName;
        public final String rootMaterialCode;
        public final String terminalMaterialCode;
        public final String terminalType;
        public final String supplyMode;
        public final String materialUnit;
        public final String pathGroupKey;
        public final Map<String, String> dayQuantities;
        public final Map<String, String> weekTotals;
        public final String rowTotal;
        public final LocalDate sourceMonth;

        public GridRow(String rowKey, String factoryCode, String warehouseCode, String warehouseName,
                        String rootMaterialCode,
                        String terminalMaterialCode, String terminalType, String supplyMode,
                        String pathGroupKey, String materialUnit, Map<String, String> dayQuantities,
                        Map<String, String> weekTotals, String rowTotal) {
            this.rowKey = rowKey;
            this.factoryCode = factoryCode;
            this.warehouseCode = warehouseCode;
            this.warehouseName = warehouseName;
            this.rootMaterialCode = rootMaterialCode;
            this.terminalMaterialCode = terminalMaterialCode;
            this.terminalType = terminalType;
            this.supplyMode = supplyMode;
            this.materialUnit = materialUnit;
            this.pathGroupKey = pathGroupKey;
            this.dayQuantities = dayQuantities;
            this.weekTotals = weekTotals;
            this.rowTotal = rowTotal;
            this.sourceMonth = null;
        }

        public GridRow(String rowKey, String factoryCode, String warehouseCode, String warehouseName,
                        String rootMaterialCode, String terminalMaterialCode, String terminalType, String supplyMode,
                        String pathGroupKey, String materialUnit, Map<String, String> dayQuantities,
                        Map<String, String> weekTotals, String rowTotal, LocalDate sourceMonth) {
            this.rowKey = rowKey; this.factoryCode = factoryCode; this.warehouseCode = warehouseCode;
            this.warehouseName = warehouseName; this.rootMaterialCode = rootMaterialCode;
            this.terminalMaterialCode = terminalMaterialCode; this.terminalType = terminalType;
            this.supplyMode = supplyMode; this.pathGroupKey = pathGroupKey; this.materialUnit = materialUnit;
            this.dayQuantities = dayQuantities; this.weekTotals = weekTotals; this.rowTotal = rowTotal;
            this.sourceMonth = sourceMonth;
        }
    }

    public static class WeekGroup {
        public final String weekKey;
        public final LocalDate weekStart;
        public final LocalDate weekEnd;
        public final List<LocalDate> days;

        public WeekGroup(String weekKey, LocalDate weekStart, LocalDate weekEnd, List<LocalDate> days) {
            this.weekKey = weekKey;
            this.weekStart = weekStart;
            this.weekEnd = weekEnd;
            this.days = days;
        }
    }
}
