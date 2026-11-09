package com.mrp.mrp.engine;

import com.mrp.mrp.domain.BusinessWeekDetail;
import com.mrp.mrp.domain.MrpDailyPlanDetail;
import com.mrp.mrp.domain.MrpTerminalDemand;
import com.mrp.mrp.domain.MrpUnallocatedQuantity;
import com.mrp.mrp.domain.ProductionCalendarDay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Versioned distribution: v4 keeps three portions; v5 uses the source month's
 * actual business-week count and shifts each portion one business week earlier.
 * Capacity and historical allocation weights do not affect the initial allocation.
 * Invalid week definitions retain their quantities in the unallocated pool.
 */
public final class RollingBusinessWeekDistributionEngine {
    private static final String MACHINE_OUTPUT = "MACHINE_OUTPUT";
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final Map<LocalDate, ProductionCalendarDay> calendar = new LinkedHashMap<>();
    private final List<BusinessWeekDetail> businessWeeks;
    private final Long runId;
    private final Long batchId;
    private final String factoryCode;
    private final boolean shiftedBusinessWeeks;

    public RollingBusinessWeekDistributionEngine(List<ProductionCalendarDay> calendarDays,
                                                List<BusinessWeekDetail> businessWeeks,
                                                Long runId, Long batchId, String factoryCode) {
        this(calendarDays, businessWeeks, runId, batchId, factoryCode, false);
    }

    public RollingBusinessWeekDistributionEngine(List<ProductionCalendarDay> calendarDays,
                                                List<BusinessWeekDetail> businessWeeks,
                                                Long runId, Long batchId, String factoryCode,
                                                boolean shiftedBusinessWeeks) {
        this.shiftedBusinessWeeks = shiftedBusinessWeeks;
        this.runId = runId;
        this.batchId = batchId;
        this.factoryCode = factoryCode;
        for (ProductionCalendarDay day : calendarDays) {
            if (day.getFactoryCode() != null && !Objects.equals(factoryCode, day.getFactoryCode())) continue;
            if (calendar.putIfAbsent(day.getCalendarDate(), day) != null)
                throw new IllegalStateException("工厂生产日历日期重复：" + factoryCode + " / " + day.getCalendarDate());
        }
        this.businessWeeks = List.copyOf(businessWeeks);
    }

    public DailyDistributionEngine.DistributionResult distribute(List<MrpTerminalDemand> demands) {
        return distribute(demands, (completed, total) -> {});
    }

    public DailyDistributionEngine.DistributionResult distribute(List<MrpTerminalDemand> demands,
            java.util.function.BiConsumer<Integer, Integer> progress) {
        Map<String, List<MrpTerminalDemand>> groups = new LinkedHashMap<>();
        for (MrpTerminalDemand demand : demands) {
            if (!Objects.equals(factoryCode, demand.getFactoryCode()))
                throw new IllegalArgumentException("排产需求不属于当前工厂：" + demand.getFactoryCode());
            if (demand.getSourceMonth() == null || demand.getQuantity() == null || demand.getQuantity().signum() < 0)
                throw new IllegalArgumentException("排产需求必须包含来源月份和非负数量");
            groups.computeIfAbsent(demandGroupKey(demand), ignored -> new ArrayList<>()).add(demand);
        }
        List<MrpDailyPlanDetail> details = new ArrayList<>();
        List<MrpUnallocatedQuantity> unallocated = new ArrayList<>();
        int completedGroups = 0;
        for (Map.Entry<String, List<MrpTerminalDemand>> group : groups.entrySet()) {
            MrpTerminalDemand demand = group.getValue().getFirst();
            BigDecimal quantity = group.getValue().stream().map(MrpTerminalDemand::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (quantity.signum() == 0) {
                progress.accept(++completedGroups, groups.size());
                continue;
            }
            int scale = MACHINE_OUTPUT.equals(demand.getTerminalType()) ? 0 : 6;
            if (quantity.stripTrailingZeros().scale() > scale)
                throw new IllegalStateException("排产需求数量精度不合法：" + quantity.toPlainString());
            // Integral terminal quantities use integral shares too; intrinsically fractional
            // BOM quantities retain six decimal places and exact conservation.
            int distributionScale = quantity.stripTrailingZeros().scale() <= 0 ? 0 : scale;
            LocalDate sourceMonth = demand.getSourceMonth().withDayOfMonth(1);
            List<BusinessWeekDetail> previous = monthWeeks(sourceMonth.minusMonths(1));
            List<BusinessWeekDetail> current = monthWeeks(sourceMonth);
            if (shiftedBusinessWeeks) {
                String invalid = current.isEmpty() ? "BUSINESS_WEEK_MISSING" : current.stream()
                        .map(this::invalidWeek).filter(Objects::nonNull).findFirst().orElse(null);
                if (invalid != null) {
                    unallocated.add(unallocated(group.getKey(), sourceMonth, sourceMonth, quantity, invalid));
                    progress.accept(++completedGroups, groups.size());
                    continue;
                }
            }
            int count = shiftedBusinessWeeks ? current.size() : 3;
            BigDecimal later = quantity.divide(BigDecimal.valueOf(count), distributionScale, RoundingMode.FLOOR);
            List<BigDecimal> portions = new ArrayList<>();
            portions.add(quantity.subtract(later.multiply(BigDecimal.valueOf(count - 1))));
            for (int i = 1; i < count; i++) portions.add(later);
            List<BusinessWeekDetail> targets = new ArrayList<>();
            targets.add(previous.isEmpty() ? null : previous.getLast());
            if (shiftedBusinessWeeks) {
                targets.addAll(current.subList(0, count - 1));
            } else {
                targets.add(findWeek(current, "W1"));
                targets.add(findWeek(current, "W2"));
            }
            for (int i = 0; i < portions.size(); i++) {
                BigDecimal portion = portions.get(i);
                if (portion.signum() == 0) continue;
                BusinessWeekDetail week = targets.get(i);
                String invalid = week == null ? "BUSINESS_WEEK_MISSING" : invalidWeek(week);
                LocalDate attempted = week == null ? (i == 0 ? sourceMonth.minusDays(1) : sourceMonth) : week.getStartDate();
                if (invalid != null) {
                    unallocated.add(unallocated(group.getKey(), sourceMonth, attempted, portion, invalid));
                    continue;
                }
                boolean incomplete = week.getStartDate().datesUntil(week.getEndDate().plusDays(1)).anyMatch(date -> !calendar.containsKey(date));
                if (incomplete) {
                    unallocated.add(unallocated(group.getKey(), sourceMonth, week.getStartDate(), portion, "CALENDAR_INCOMPLETE"));
                    continue;
                }
                List<LocalDate> productionDates = calendar.entrySet().stream()
                        .filter(entry -> !entry.getKey().isBefore(week.getStartDate()) && !entry.getKey().isAfter(week.getEndDate()))
                        .filter(entry -> Boolean.TRUE.equals(entry.getValue().getIsProductionDay()))
                        .map(Map.Entry::getKey).sorted().toList();
                if (productionDates.isEmpty()) {
                    unallocated.add(unallocated(group.getKey(), sourceMonth, week.getStartDate(), portion, "NO_PRODUCTION_DAY"));
                    continue;
                }
                List<BigDecimal> daily = equalShares(portion, productionDates.size(), distributionScale);
                for (int dayIndex = 0; dayIndex < productionDates.size(); dayIndex++) {
                    if (daily.get(dayIndex).signum() == 0) continue;
                    details.add(detail(demand, group.getKey(), productionDates.get(dayIndex), daily.get(dayIndex)));
                }
            }
            progress.accept(++completedGroups, groups.size());
        }
        return new DailyDistributionEngine.DistributionResult(details, unallocated);
    }

    private List<BusinessWeekDetail> monthWeeks(LocalDate month) {
        return businessWeeks.stream().filter(week -> week.getBusinessMonth() != null
                        && month.equals(week.getBusinessMonth().withDayOfMonth(1)))
                .sorted(Comparator.comparing(BusinessWeekDetail::getSortNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(BusinessWeekDetail::getStartDate, Comparator.nullsLast(LocalDate::compareTo))
                        .thenComparing(BusinessWeekDetail::getWeekCode, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private BusinessWeekDetail findWeek(List<BusinessWeekDetail> weeks, String code) {
        return weeks.stream().filter(week -> code.equals(week.getWeekCode())).findFirst().orElse(null);
    }

    private String invalidWeek(BusinessWeekDetail week) {
        if (week.getStartDate() == null || week.getEndDate() == null || week.getStartDate().isAfter(week.getEndDate()))
            return "BUSINESS_WEEK_INVALID";
        for (BusinessWeekDetail other : businessWeeks) {
            if (other == week) continue;
            if (other.getStartDate() == null || other.getEndDate() == null) continue;
            if (!week.getStartDate().isAfter(other.getEndDate()) && !week.getEndDate().isBefore(other.getStartDate()))
                return "BUSINESS_WEEK_OVERLAP";
            if (Objects.equals(week.getBusinessMonth(), other.getBusinessMonth())
                    && Objects.equals(week.getWeekCode(), other.getWeekCode())) return "BUSINESS_WEEK_INVALID";
        }
        return null;
    }

    private List<BigDecimal> equalShares(BigDecimal total, int count, int scale) {
        BigDecimal base = total.divide(BigDecimal.valueOf(count), scale, RoundingMode.FLOOR);
        BigDecimal unit = BigDecimal.ONE.movePointLeft(scale);
        int remainder = total.subtract(base.multiply(BigDecimal.valueOf(count)))
                .divide(unit, 0, RoundingMode.UNNECESSARY).intValueExact();
        List<BigDecimal> shares = new ArrayList<>();
        for (int i = 0; i < count; i++) shares.add(base.add(i < remainder ? unit : BigDecimal.ZERO));
        return shares;
    }

    private MrpDailyPlanDetail detail(MrpTerminalDemand demand, String groupKey, LocalDate date, BigDecimal quantity) {
        MrpDailyPlanDetail detail = new MrpDailyPlanDetail();
        detail.setRunId(runId);
        detail.setBatchId(batchId);
        detail.setFactoryCode(factoryCode);
        detail.setRootMaterialCode(demand.getRootMaterialCode());
        detail.setTerminalMaterialCode(demand.getTerminalMaterialCode());
        detail.setTerminalType(demand.getTerminalType());
        detail.setSupplyMode(demand.getSupplyMode());
        detail.setPathGroupKey(groupKey);
        detail.setSourceMonth(demand.getSourceMonth().withDayOfMonth(1));
        // v4 has one production-date basis. Source month retains FCST provenance;
        // sourceDate no longer means a fictitious date seven days after production.
        detail.setSourceDate(date);
        detail.setScheduleDate(date);
        detail.setSystemQuantity(quantity);
        detail.setIsProductionDay(true);
        detail.setRevision(0);
        String identity = String.join("|", factoryCode, demand.getRootMaterialCode(), demand.getTerminalMaterialCode(),
                demand.getTerminalType(), demand.getSupplyMode(), groupKey,
                detail.getSourceMonth().toString(), date.toString());
        try {
            detail.setRowKey(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
        return detail;
    }

    private MrpUnallocatedQuantity unallocated(String groupKey, LocalDate sourceMonth, LocalDate target,
                                              BigDecimal quantity, String reason) {
        MrpUnallocatedQuantity unallocated = new MrpUnallocatedQuantity();
        unallocated.setRunId(runId);
        unallocated.setBatchId(batchId);
        unallocated.setDemandKey(groupKey);
        unallocated.setSourceDate(sourceMonth);
        unallocated.setAttemptedScheduleDate(target);
        unallocated.setQuantity(quantity);
        unallocated.setReasonCode(reason);
        unallocated.setStatus("PENDING");
        unallocated.setRevision(0);
        return unallocated;
    }

    /** Matches persisted historical demand grouping so conservation/revision checks remain usable. */
    public static String demandGroupKey(MrpTerminalDemand demand) {
        return demand.getFactoryCode() + "|" + demand.getRootMaterialCode() + "|" + demand.getTerminalMaterialCode() + "|"
                + demand.getTerminalType() + "|" + demand.getSupplyMode() + "|"
                + demand.getSourceMonth().format(MONTH_FORMAT);
    }
}
