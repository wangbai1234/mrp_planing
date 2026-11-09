package com.mrp.mrp.service;

import com.mrp.forecast.repository.ForecastMapper;
import com.mrp.mrp.domain.*;
import com.mrp.mrp.repository.*;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

/** Completeness of the dates consumed by distribution, including the preceding business week. */
@Service
public class PlanningCalendarValidationService {
    private final ForecastMapper forecasts;
    private final MrpCalcBatchMapper batches;
    private final ConfigMasterMapper master;
    private final ProductionCalendarDayMapper calendars;

    public PlanningCalendarValidationService(ForecastMapper forecasts, MrpCalcBatchMapper batches,
            ConfigMasterMapper master, ProductionCalendarDayMapper calendars) {
        this.forecasts = forecasts; this.batches = batches; this.master = master; this.calendars = calendars;
    }
    public record MissingDay(String factoryCode, LocalDate date) {}
    public record CalendarCheck(int total, List<MissingDay> missingDays, List<String> configurationIssues) {}

    public CalendarCheck checkBatch(Long batchId, boolean latest) {
        var batch = batches.selectById(batchId);
        if (batch == null) throw new IllegalArgumentException("排产版本不存在");
        return check(batch, latest);
    }

    public CalendarCheck check(MrpCalcBatch batch, boolean latest) {
        Map<String, Set<LocalDate>> months = new TreeMap<>();
        List<String> frozenFactories = batches.selectFactoryCodesByBatchId(batch.getId());
        List<String> selectedFactories = frozenFactories.isEmpty() ? ProductionCapacityRules.factories(batch) : frozenFactories;
        for (var row : forecasts.selectDetailsByVersionId(batch.getForecastVersionId())) {
            if (!selectedFactories.contains(row.factoryCode())
                    || row.planMonth() == null || row.forecastQty() == null || row.forecastQty().signum() <= 0) continue;
            months.computeIfAbsent(row.factoryCode(), ignored -> new TreeSet<>()).add(row.planMonth().withDayOfMonth(1));
        }
        if (months.isEmpty()) return new CalendarCheck(0, List.of(), List.of());
        LocalDate first = months.values().stream().flatMap(Set::stream).min(LocalDate::compareTo).orElseThrow();
        LocalDate last = months.values().stream().flatMap(Set::stream).max(LocalDate::compareTo).orElseThrow();
        List<BusinessWeekDetail> weeks = new ArrayList<>(batches.selectBusinessWeeksByBatchAndRange(
                batch.getId(), first.minusMonths(1), last.plusMonths(1)));
        // Revisions retain frozen week definitions, filling only months not yet scoped.
        if (latest && ProductionCapacityRules.rolling(batch)) {
            Set<LocalDate> scoped = new HashSet<>(batches.selectBusinessWeekMonthsByBatchId(batch.getId()));
            Set<LocalDate> required = new TreeSet<>();
            months.values().forEach(values -> values.forEach(month -> { required.add(month); required.add(month.minusMonths(1)); }));
            for (var month : required) if (!scoped.contains(month)) {
                for (var row : master.selectBusinessWeeks(month)) {
                    var week = new BusinessWeekDetail(); week.setBusinessMonth(month); week.setWeekCode(row.getWeekCode());
                    week.setSortNo(row.getSortNo()); week.setStartDate(row.getStartDate()); week.setEndDate(row.getEndDate());
                    weeks.add(week);
                }
            }
        }
        List<ProductionCalendarDay> frozen = latest || batch.getProductionCalendarVersionId() == null
                ? List.of() : calendars.selectByVersionId(batch.getProductionCalendarVersionId());
        List<MissingDay> missing = new ArrayList<>();
        Set<String> issues = new LinkedHashSet<>();
        int total = 0;
        for (var entry : months.entrySet()) {
            Set<LocalDate> dates = new TreeSet<>();
            for (LocalDate month : entry.getValue()) {
                if (ProductionCapacityRules.rolling(batch)) {
                    List<BusinessWeekDetail> previous = monthWeeks(weeks, month.minusMonths(1));
                    List<BusinessWeekDetail> current = monthWeeks(weeks, month);
                    List<BusinessWeekDetail> targets = new ArrayList<>();
                    if (previous.isEmpty()) issues.add(month.minusMonths(1) + " 缺少业务周配置");
                    else targets.add(previous.getLast());
                    if (current.isEmpty()) issues.add(month + " 缺少业务周配置");
                    else if (ProductionCapacityRules.shiftedBusinessWeeks(batch)) targets.addAll(current.subList(0, current.size() - 1));
                    else for (String code : List.of("W1", "W2")) {
                        var target = current.stream().filter(w -> code.equals(w.getWeekCode())).findFirst();
                        if (target.isPresent()) targets.add(target.get()); else issues.add(month + " 缺少业务周 " + code);
                    }
                    for (var week : targets) {
                        if (week.getStartDate() == null || week.getEndDate() == null || week.getStartDate().isAfter(week.getEndDate()))
                            issues.add(month + " 业务周日期无效");
                        else week.getStartDate().datesUntil(week.getEndDate().plusDays(1)).forEach(dates::add);
                    }
                } else {
                    // Historical rules select source production days, then shift seven days.
                    month.minusDays(7).datesUntil(month.plusMonths(1)).forEach(dates::add);
                }
            }
            Set<LocalDate> configured = new HashSet<>();
            if (latest) {
                for (var day : master.selectProductionCalendar(entry.getKey()))
                    if (day.getProductionDay() != null) configured.add(day.getCalendarDate());
            } else for (var day : frozen)
                if (entry.getKey().equals(day.getFactoryCode()) && day.getIsProductionDay() != null) configured.add(day.getCalendarDate());
            for (LocalDate date : dates) if (!configured.contains(date)) {
                total++;
                if (missing.size() < 20) missing.add(new MissingDay(entry.getKey(), date));
            }
        }
        return new CalendarCheck(total + issues.size(), List.copyOf(missing), List.copyOf(issues));
    }

    private List<BusinessWeekDetail> monthWeeks(List<BusinessWeekDetail> weeks, LocalDate month) {
        return weeks.stream().filter(w -> w.getBusinessMonth() != null && month.equals(w.getBusinessMonth().withDayOfMonth(1)))
                .sorted(Comparator.comparing(BusinessWeekDetail::getSortNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(BusinessWeekDetail::getStartDate, Comparator.nullsLast(LocalDate::compareTo))
                        .thenComparing(BusinessWeekDetail::getWeekCode, Comparator.nullsLast(String::compareTo))).toList();
    }
}
