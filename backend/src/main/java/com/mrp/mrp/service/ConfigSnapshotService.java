package com.mrp.mrp.service;

import com.mrp.mrp.domain.FactoryWarehouseMapping;
import com.mrp.mrp.repository.ConfigMasterMapper;
import com.mrp.mrp.repository.Phase2ScopeConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 创建批次时把五项配置的当前主数据复制成不可变快照。
 * 快照沿用旧版本表，status 为 SNAPSHOT，id 写回批次；之后主数据怎么改都不影响该批次。
 */
@Service
public class ConfigSnapshotService {
    private final com.mrp.mrp.repository.DailyCapacityMapper capacity;
    private final ConfigMasterMapper master;
    private final Phase2ScopeConfigMapper scopeConfig;

    public ConfigSnapshotService(ConfigMasterMapper master, Phase2ScopeConfigMapper scopeConfig, com.mrp.mrp.repository.DailyCapacityMapper capacity) {
        this.capacity = capacity;
        this.master = master;
        this.scopeConfig = scopeConfig;
    }

    public FactoryWarehouseMapping mappingFor(String factoryCode) {
        return master.selectFactoryWarehouseMappingByFactory(factoryCode);
    }

    public boolean hasStockPolicy() { return master.countStockPolicies() > 0; }

    public boolean hasMaterialPolicy() { return master.countMaterialPolicies() > 0; }

    public boolean hasCalendar(String factoryCode) {
        return master.selectProductionCalendarFactories().contains(factoryCode);
    }

    public List<LocalDate> businessMonths() { return master.selectBusinessMonths(); }

    /** Keep required forecast months first; include maintained months reached by the seven-day lead time. */
    public List<LocalDate> dailyPlanningBusinessMonths(List<LocalDate> sourceMonths) {
        var months = new LinkedHashSet<>(sourceMonths);
        var maintained = new LinkedHashSet<>(businessMonths());
        for (LocalDate sourceMonth : sourceMonths) {
            LocalDate first = sourceMonth.withDayOfMonth(1);
            LocalDate lastScheduleMonth = first.plusMonths(1).minusDays(8).withDayOfMonth(1);
            for (LocalDate month = first.minusDays(7).withDayOfMonth(1);
                 !month.isAfter(lastScheduleMonth); month = month.plusMonths(1)) {
                if (maintained.contains(month)) months.add(month);
            }
        }
        return List.copyOf(months);
    }

    /** v4 requires both source business months and their previous month, including the first imported month. */
    public List<LocalDate> rollingBusinessMonths(List<LocalDate> sourceMonths) {
        var months = new LinkedHashSet<LocalDate>();
        sourceMonths.stream().map(month -> month.withDayOfMonth(1)).sorted().forEach(month -> {
            months.add(month.minusMonths(1)); months.add(month);
        });
        return List.copyOf(months);
    }

    /** @return 五项快照的版本 id：mapping、stockPolicy、materialPolicy、calendar，以及 businessMonth→weekVersionId */
    @Transactional
    public Snapshot snapshot(String factoryCode, List<LocalDate> months, Long userId) {
        int mappingNo = scopeConfig.nextFactoryWarehouseVersionNo();
        master.insertFactoryWarehouseSnapshot(mappingNo, userId);
        long mappingId = master.selectLastInsertId();
        master.copyFactoryWarehouseSnapshotDetails(mappingId);

        int stockNo = scopeConfig.nextStockPolicyVersionNo();
        master.insertStockPolicySnapshot(stockNo, userId);
        long stockId = master.selectLastInsertId();
        master.copyStockPolicySnapshotDetails(stockId);

        master.insertMaterialPolicySnapshot(null, userId);
        long materialId = master.selectLastInsertId();
        master.copyMaterialPolicySnapshotDetails(materialId);

        long calendarId = snapshotCalendar(factoryCode, userId);

        Map<LocalDate, Long> weeks = snapshotBusinessWeeks(months, userId);
        return new Snapshot(mappingId, stockId, materialId, calendarId, weeks);
    }

    @Transactional
    public Map<LocalDate, Long> snapshotBusinessWeeks(List<LocalDate> months, Long userId) {
        Map<LocalDate, Long> weeks = new LinkedHashMap<>();
        for (LocalDate month : months) {
            int weekNo = scopeConfig.nextBusinessWeekVersionNo(month);
            master.insertBusinessWeekSnapshot(weekNo, month, userId);
            long weekId = master.selectLastInsertId();
            master.copyBusinessWeekSnapshotDetails(weekId, month);
            weeks.put(month, weekId);
        }
        return weeks;
    }

    @Transactional
    public long snapshotCalendar(String factoryCode, Long userId) {
        master.insertProductionCalendarSnapshot(null, factoryCode, userId);
        long id = master.selectLastInsertId();
        master.copyProductionCalendarSnapshotDetails(id, factoryCode);
        capacity.freezeUnits(id);
        return id;
    }

    public record Snapshot(long mappingVersionId, long stockPolicyVersionId, long materialPolicyVersionId,
                           long calendarVersionId, Map<LocalDate, Long> businessWeekVersionIds) {}
}
