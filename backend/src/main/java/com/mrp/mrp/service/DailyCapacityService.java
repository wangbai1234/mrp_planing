package com.mrp.mrp.service;

import com.mrp.mrp.domain.*;
import com.mrp.mrp.repository.DailyCapacityMapper;
import com.mrp.mrp.repository.ProductionCalendarDayMapper;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class DailyCapacityService {
    private final DailyCapacityMapper mapper;
    private final ProductionCalendarDayMapper calendar;
    private final com.mrp.mrp.repository.MrpCalcBatchMapper batches;
    public DailyCapacityService(DailyCapacityMapper mapper, ProductionCalendarDayMapper calendar,
                                com.mrp.mrp.repository.MrpCalcBatchMapper batches) {
        this.mapper = mapper; this.calendar = calendar; this.batches = batches;
    }
    public Map<String, String> units(Long versionId) {
        Map<String, String> result = new HashMap<>();
        if (versionId != null) for (var row : mapper.selectUnits(versionId)) result.put(row.materialCode(), row.unit());
        return result;
    }
    public List<DayLoad> evaluate(MrpCalcBatch batch, List<MrpDailyPlanDetail> plans, LocalDate start, LocalDate end) {
        List<ProductionCalendarDay> frozen = batch.getProductionCalendarVersionId() == null ? List.of()
                : calendar.selectByVersionId(batch.getProductionCalendarVersionId());
        List<DayLoad> loads = new ArrayList<>();
        List<String> frozenFactories = batch.getId() == null ? List.of() : batches.selectFactoryCodesByBatchId(batch.getId());
        for (String factory : frozenFactories.isEmpty() ? ProductionCapacityRules.factories(batch) : frozenFactories) {
            Map<LocalDate, ProductionCalendarDay> days = new HashMap<>();
            for (var day : frozen) if (Objects.equals(factory, day.getFactoryCode())) days.put(day.getCalendarDate(), day);
            loads.addAll(ProductionCapacityRules.wholeMachine(batch)
                    ? evaluateMachineOutput(factory, plans, days, start, end)
                    : evaluate(factory, plans, days, units(batch.getProductionCalendarVersionId()), start, end));
        }
        return loads;
    }
    /** All effective facts of one plan are evaluated before display filtering/pagination. */
    public static List<DayLoad> evaluate(String factory, List<MrpDailyPlanDetail> plans,
            Map<LocalDate, ProductionCalendarDay> days, Map<String, String> units, LocalDate start, LocalDate end) {
        Map<LocalDate, List<MrpDailyPlanDetail>> byDate = new HashMap<>();
        for (var plan : plans) {
            if (Objects.equals(factory, plan.getFactoryCode()) && !plan.getScheduleDate().isBefore(start)
                    && !plan.getScheduleDate().isAfter(end))
                byDate.computeIfAbsent(plan.getScheduleDate(), ignored -> new ArrayList<>()).add(plan);
        }
        List<DayLoad> result = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            ProductionCalendarDay day = days.get(date);
            BigDecimal load = BigDecimal.ZERO;
            boolean unknown = false;
            boolean anyOwnLoad = false;
            for (var plan : byDate.getOrDefault(date, List.of())) {
                if (!Objects.equals(factory, plan.getFactoryCode()) || !date.equals(plan.getScheduleDate())
                        || plan.getEffectiveQuantity().signum() == 0) continue;
                if (ProductionCapacityRules.MACHINE_OUTPUT.equals(plan.getTerminalType())) continue;
                String supply = plan.getSupplyMode();
                if ("PURCHASED".equals(supply) || "OUTSOURCED".equals(supply)) continue;
                if (!"SELF_MADE".equals(supply)) { unknown = true; continue; }
                anyOwnLoad = true;
                String unit = units.get(plan.getTerminalMaterialCode());
                if (unit == null || unit.isBlank() || day == null || day.getCapacityUnit() == null
                        || !unit.equals(day.getCapacityUnit())) { unknown = true; continue; }
                load = load.add(plan.getEffectiveQuantity());
            }
            BigDecimal capacity = day == null ? null : day.getDailyCapacity();
            boolean rest = day != null && Boolean.FALSE.equals(day.getIsProductionDay());
            boolean overload = capacity != null && load.compareTo(capacity) > 0;
            String status;
            String message;
            if (rest && anyOwnLoad) { status = "REST_LOAD"; message = "停产日存在排产"; }
            else if (rest && !unknown) { status = "NORMAL"; message = "停产"; }
            else if (overload) { status = "OVERLOAD"; message = unknown ? "超产能；部分需求未评估" : "超产能"; }
            else if (unknown || day == null || capacity == null || (day.getCapacityUnit() == null || day.getCapacityUnit().isBlank())
                    || (!rest && capacity.signum() <= 0)) { status = "UNASSESSED"; message = "产能未评估"; }
            else { status = "NORMAL"; message = rest ? "停产" : load.compareTo(capacity) == 0 ? "满产" : "正常"; }
            result.add(new DayLoad(factory, date, day != null, !rest && day != null, capacity,
                    day == null ? null : day.getCapacityUnit(), load,
                    capacity == null ? null : load.subtract(capacity).max(BigDecimal.ZERO), status, message, unknown, ProductionCapacityRules.TERMINAL));
        }
        return result;
    }
    public static List<DayLoad> evaluateMachineOutput(String factory, List<MrpDailyPlanDetail> plans,
            Map<LocalDate, ProductionCalendarDay> days, LocalDate start, LocalDate end) {
        Map<LocalDate, BigDecimal> loads = new HashMap<>();
        for (var plan : plans) {
            if (Objects.equals(factory, plan.getFactoryCode()) && ProductionCapacityRules.MACHINE_OUTPUT.equals(plan.getTerminalType()))
                loads.merge(plan.getScheduleDate(), plan.getEffectiveQuantity(), BigDecimal::add);
        }
        List<DayLoad> result = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            var day = days.get(date);
            BigDecimal load = loads.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal capacity = day == null ? null : day.getDailyCapacity();
            boolean rest = day != null && Boolean.FALSE.equals(day.getIsProductionDay());
            boolean valid = day != null && "台".equals(day.getCapacityUnit()) && capacity != null
                    && capacity.stripTrailingZeros().scale() <= 0 && (rest ? capacity.signum() == 0 : capacity.signum() > 0);
            String status, message;
            if (rest && load.signum() > 0) { status = "REST_LOAD"; message = "停产日存在整机排产"; }
            else if (rest && load.signum() == 0) { status = "NORMAL"; message = "停产"; }
            else if (!valid) { status = "UNASSESSED"; message = "整机产能未评估，请配置整数台/日产能"; }
            else if (load.compareTo(capacity) > 0) { status = "OVERLOAD"; message = "超整机日产能"; }
            else { status = "NORMAL"; message = load.compareTo(capacity) == 0 ? "满产" : "正常"; }
            result.add(new DayLoad(factory, date, day != null, day != null && !rest, capacity,
                    day == null ? null : day.getCapacityUnit(), load, valid ? load.subtract(capacity).max(BigDecimal.ZERO) : null,
                    status, message, !valid && !rest, ProductionCapacityRules.MACHINE_OUTPUT));
        }
        return result;
    }
    public record DayLoad(String factoryCode, LocalDate date, boolean configured, boolean productionDay,
            @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal dailyCapacity, String capacityUnit, @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal load, @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal excess,
            String status, String message, boolean partiallyUnassessed, String capacityBasis) {}
}
