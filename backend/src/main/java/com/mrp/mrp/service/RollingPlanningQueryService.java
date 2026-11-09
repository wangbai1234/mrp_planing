package com.mrp.mrp.service;

import com.mrp.mrp.controller.MrpDailyPlanningController;
import com.mrp.mrp.domain.*;
import com.mrp.mrp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** A single, fully continuous date axis; database pagination applies to visual material rows. */
@Service
public class RollingPlanningQueryService {
    private final RollingPlanningMapper mapper;
    private final MrpCalcBatchMapper batches;
    private final DailyPlanningService daily;
    private final DailyCapacityService capacity;
    private final MrpDailyExecutionMapper executions;
    private final PlanningFactoryAccessService access;
    public RollingPlanningQueryService(RollingPlanningMapper mapper, MrpCalcBatchMapper batches,
            DailyPlanningService daily, DailyCapacityService capacity, MrpDailyExecutionMapper executions, PlanningFactoryAccessService access) {
        this.mapper=mapper; this.batches=batches; this.daily=daily; this.capacity=capacity; this.executions=executions; this.access=access;
    }
    public static boolean rolling(MrpCalcBatch batch) {
        return ProductionCapacityRules.rolling(batch);
    }
    @Transactional(readOnly=true)
    public MrpDailyPlanningController.DailyGridResponse grid(Long batchId, int page, int pageSize, String factory, String keyword) {
        return grid(batchId, page, pageSize, factory, keyword, "TERMINAL", true);
    }
    @Transactional(readOnly=true)
    public MrpDailyPlanningController.DailyGridResponse grid(Long batchId, int page, int pageSize, String factory, String keyword, boolean includeCapacity) {
        return grid(batchId, page, pageSize, factory, keyword, "TERMINAL", includeCapacity);
    }
    @Transactional(readOnly=true)
    public MrpDailyPlanningController.DailyGridResponse grid(Long batchId, int page, int pageSize, String factory, String keyword, String view, boolean includeCapacity) {
        return grid(batchId,page,pageSize,factory,keyword,null,view,includeCapacity);
    }
    @Transactional(readOnly=true)
    public MrpDailyPlanningController.DailyGridResponse grid(Long batchId, int page, int pageSize, String factory, String keyword, String rootMaterialCode, String view, boolean includeCapacity) {
        if (!Set.of("MACHINE_OUTPUT", "TERMINAL").contains(view)) throw new IllegalArgumentException("排产视图不合法");
        validatePage(page,pageSize);
        MrpCalcBatch batch=requireBatch(batchId);
        Long run=batch.getActiveRunId();
        List<LocalDate> months=mapper.selectMonths(batch.getForecastVersionId()).stream().filter(Objects::nonNull)
                .map(date->date.withDayOfMonth(1)).distinct().sorted().toList();
        List<LocalDate> factDates=run==null ? List.of() : mapper.selectFactDates(run,batchId).stream().filter(Objects::nonNull).toList();
        LocalDate start=months.isEmpty() ? factDates.stream().min(LocalDate::compareTo).orElse(LocalDate.now()) : months.getFirst();
        LocalDate end=months.isEmpty() ? factDates.stream().max(LocalDate::compareTo).orElse(start) : months.getLast().plusMonths(1).minusDays(1);
        List<BusinessWeekDetail> weeks=daily.getBusinessWeeks(batchId,start.minusMonths(1).withDayOfMonth(1),end.plusMonths(1));
        LocalDate firstMonth=start.withDayOfMonth(1);
        Optional<BusinessWeekDetail> preceding=weeks.stream().filter(w->w.getBusinessMonth()!=null
                && w.getBusinessMonth().withDayOfMonth(1).equals(firstMonth.minusMonths(1)))
                .max(Comparator.comparing(BusinessWeekDetail::getSortNo,Comparator.nullsFirst(Integer::compareTo))
                        .thenComparing(BusinessWeekDetail::getStartDate));
        if(preceding.isPresent() && preceding.get().getStartDate().isBefore(start)) start=preceding.get().getStartDate();
        for(LocalDate date:factDates) { if(date.isBefore(start))start=date; if(date.isAfter(end))end=date; }
        List<LocalDate> dates=start.datesUntil(end.plusDays(1)).toList();
        List<MrpDailyPlanningController.WeekGroup> weekGroups=new ArrayList<>();
        for(var week:weeks) {
            LocalDate from=week.getStartDate().isBefore(start)?start:week.getStartDate();
            LocalDate to=week.getEndDate().isAfter(end)?end:week.getEndDate();
            if(!from.isAfter(to))weekGroups.add(new MrpDailyPlanningController.WeekGroup(daily.businessWeekKey(week),week.getStartDate(),week.getEndDate(),from.datesUntil(to.plusDays(1)).toList()));
        }
        Map<String,List<FactoryWarehouseMappingDetail>> warehouses=batches.selectFactoryWarehousesByBatchId(batchId).stream()
                .collect(Collectors.groupingBy(FactoryWarehouseMappingDetail::getFactoryCode,LinkedHashMap::new,Collectors.toList()));
        Map<String,String> units=capacity.units(batch.getProductionCalendarVersionId());
        boolean combinedMachine="MACHINE_OUTPUT".equals(view) && ProductionCapacityRules.shiftedBusinessWeeks(batch);
        long total=run==null?0:combinedMachine
                ?mapper.countMachineRows(run,blank(factory),blank(keyword),blank(rootMaterialCode))
                :mapper.countRows(run,blank(factory),blank(keyword),blank(rootMaterialCode),view);
        List<MrpDailyPlanDetail> keys=run==null?List.of():combinedMachine
                ?mapper.selectMachineRowPage(run,blank(factory),blank(keyword),blank(rootMaterialCode),(long)(page-1)*pageSize,pageSize)
                :mapper.selectRowPage(run,blank(factory),blank(keyword),blank(rootMaterialCode),view,(long)(page-1)*pageSize,pageSize);
        List<MrpDailyPlanDetail> facts=keys.isEmpty()?List.of():daily.applyOverrides(batchId,combinedMachine
                ?mapper.selectMachineRowFacts(run,keys):mapper.selectRowFacts(run,keys));
        Map<String,List<MrpDailyPlanDetail>> byRow=facts.stream().collect(Collectors.groupingBy(
                fact->combinedMachine?machineVisualKey(fact):visualKey(fact),LinkedHashMap::new,Collectors.toList()));
        List<MrpDailyPlanningController.GridRow> rows=new ArrayList<>();
        for(var key:keys) {
            var grouped=byRow.getOrDefault(combinedMachine?machineVisualKey(key):visualKey(key),List.of());
            Map<String,BigDecimal> quantities=new LinkedHashMap<>(), weekly=new LinkedHashMap<>();
            BigDecimal rowTotal=BigDecimal.ZERO;
            for(var fact:grouped) {
                quantities.merge(fact.getScheduleDate().toString(),fact.getEffectiveQuantity(),BigDecimal::add);
                var week=daily.findBusinessWeek(weeks,fact.getScheduleDate());
                if(week!=null)weekly.merge(daily.businessWeekKey(week),fact.getEffectiveQuantity(),BigDecimal::add);
                rowTotal=rowTotal.add(fact.getEffectiveQuantity());
            }
            var factoryWarehouses=warehouses.getOrDefault(key.getFactoryCode(),List.of());
            String warehouseCodes=factoryWarehouses.stream().map(FactoryWarehouseMappingDetail::getWarehouseCode)
                    .filter(Objects::nonNull).distinct().collect(Collectors.joining("、"));
            String warehouseNames=factoryWarehouses.stream().map(item -> item.getWarehouseName()==null || item.getWarehouseName().isBlank()
                    ? item.getWarehouseCode() : item.getWarehouseName()).filter(Objects::nonNull).distinct().collect(Collectors.joining("、"));
            rows.add(new MrpDailyPlanningController.GridRow(combinedMachine?machineVisualKey(key):visualKey(key),key.getFactoryCode(),warehouseCodes.isEmpty()?null:warehouseCodes,
                    warehouseNames.isEmpty()?null:warehouseNames,key.getRootMaterialCode(),key.getTerminalMaterialCode(),key.getTerminalType(),
                    key.getSupplyMode(),"",units.get(key.getTerminalMaterialCode()),strings(quantities),strings(weekly),text(rowTotal),combinedMachine?null:key.getSourceMonth()));
        }
        List<MrpDailyPlanDetail> machineFacts=!includeCapacity || run==null || !ProductionCapacityRules.wholeMachine(batch)?List.of()
                :mapper.selectMachineLoads(run,batchId,rolling(batch));
        return new MrpDailyPlanningController.DailyGridResponse(rows,Math.toIntExact(total),page,pageSize,dates,weekGroups,List.of(),
                includeCapacity && ProductionCapacityRules.wholeMachine(batch) ? capacity.evaluate(batch,machineFacts,start,end) : List.of(),
                run,view,ProductionCapacityRules.basis(batch),batch.getRuleVersion());
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public DetailPage details(Long batchId,int page,int pageSize,String factory,String root,String terminal,String type,LocalDate start,LocalDate end) {
        return details(batchId,page,pageSize,factory,root,terminal,type,null,start,end);
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public DetailPage details(Long batchId,int page,int pageSize,String factory,String root,String terminal,String type,LocalDate sourceMonth,LocalDate start,LocalDate end) {
        validatePage(page,pageSize);
        if(start==null||end==null||start.isAfter(end))throw new IllegalArgumentException("请选择有效日期范围");
        MrpCalcBatch batch=requireBatch(batchId);
        if(batch.getActiveRunId()==null)return new DetailPage(List.of(),0,page,pageSize);
        // Existence, total and page share one snapshot even when a planner commits an adjustment concurrently.
        boolean hasOverrides=mapper.hasOverrides(batchId);
        long total=mapper.countDetails(batch.getActiveRunId(),batchId,blank(factory),blank(root),blank(terminal),blank(type),sourceMonth,start,end,rolling(batch),hasOverrides);
        Long virtualCount=null;
        // Keep selective lookups on their existing indexes. For any number V of new
        // dates, merge at most min(offset,V)+pageSize stored keys with V virtual keys;
        // only the final page reads full payloads. Existence/count/page share this RR snapshot.
        if(hasOverrides && sourceMonth==null && blank(factory)==null && blank(root)==null && blank(terminal)==null) {
            virtualCount=mapper.countVirtualDetails(batch.getActiveRunId(),batchId,null,null,null,blank(type),sourceMonth,start,end);
        }
        var facts=mapper.selectDetails(batch.getActiveRunId(),batchId,blank(factory),blank(root),blank(terminal),blank(type),sourceMonth,start,end,rolling(batch),hasOverrides,(long)(page-1)*pageSize,pageSize,virtualCount);
        Map<LocalDate,String> protectedDays=new HashMap<>();
        for(var record:executions.selectByBatchAndDateRange(batchId,start,end)) {
            if("LOCKED".equals(record.getExecutionStatus())||"EXECUTED".equals(record.getExecutionStatus()))protectedDays.put(record.getScheduleDate(),record.getExecutionStatus());
        }
        Map<String,String> units=capacity.units(batch.getProductionCalendarVersionId());
        return new DetailPage(facts.stream().map(p->new CellFact(DailyPlanningService.gridRowKey(p),p.getRowKey(),p.getFactoryCode(),
                p.getRootMaterialCode(),p.getTerminalMaterialCode(),p.getTerminalType(),p.getSupplyMode(),p.getPathGroupKey(),p.getSourceMonth(),
                p.getSourceDate(),p.getScheduleDate(),text(p.getSystemQuantity()),text(p.getEffectiveQuantity()),p.getRevision(),
                ProductionCapacityRules.MACHINE_OUTPUT.equals(p.getTerminalType())?"台":units.get(p.getTerminalMaterialCode()),protectedDays.get(p.getScheduleDate()))).toList(),total,page,pageSize);
    }
    private MrpCalcBatch requireBatch(Long id){var b=batches.selectById(id);if(b==null)throw new IllegalArgumentException("计划不存在");access.require(b);return b;}
    public static void validatePage(int page,int size){if(page<1||size<1||size>100)throw new IllegalArgumentException("分页参数不合法");}
    private static String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    public static String visualKey(MrpDailyPlanDetail p){return String.join("|",p.getFactoryCode(),p.getRootMaterialCode(),p.getTerminalMaterialCode(),p.getTerminalType(),p.getSupplyMode(),String.valueOf(p.getSourceMonth()));}
    private static String machineVisualKey(MrpDailyPlanDetail p){return p.getFactoryCode()+"|"+p.getRootMaterialCode()+"|MACHINE_OUTPUT";}
    public static String text(BigDecimal value){return value.stripTrailingZeros().toPlainString();}
    private static Map<String,String> strings(Map<String,BigDecimal> values){Map<String,String>result=new LinkedHashMap<>();values.forEach((k,v)->result.put(k,text(v)));return result;}
    public record DetailPage(List<CellFact> items,long total,int page,int pageSize){}
    public record CellFact(String rowKey,String factRowKey,String factoryCode,String rootMaterialCode,String terminalMaterialCode,
            String terminalType,String supplyMode,String pathGroupKey,LocalDate sourceMonth,LocalDate sourceDate,LocalDate scheduleDate,
            String systemQuantity,String effectiveQuantity,Integer revision,String materialUnit,String executionStatus){}
}
