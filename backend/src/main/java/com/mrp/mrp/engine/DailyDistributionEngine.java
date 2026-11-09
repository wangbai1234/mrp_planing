package com.mrp.mrp.engine;

import com.mrp.mrp.domain.MaterialPolicyDetail;
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
import java.util.*;

public class DailyDistributionEngine {

    private static final int LEAD_TIME_DAYS = 7;

    private final Map<LocalDate, ProductionCalendarDay> calendarMap;
    private final Long runId;
    private final Long batchId;
    private final String factoryCode;

    public DailyDistributionEngine(List<ProductionCalendarDay> calendarDays, Long runId, Long batchId,
                                    String factoryCode, Map<String, MaterialPolicyDetail> materialPolicyMap) {
        this.calendarMap = new LinkedHashMap<>();
        for (ProductionCalendarDay day : calendarDays) {
            calendarMap.put(day.getCalendarDate(), day);
        }
        this.runId = runId;
        this.batchId = batchId;
        this.factoryCode = factoryCode;
    }

    public DistributionResult distribute(List<MrpTerminalDemand> terminalDemands) {
        return distribute(terminalDemands, (completed, total) -> {});
    }

    public DistributionResult distribute(List<MrpTerminalDemand> terminalDemands,
            java.util.function.BiConsumer<Integer, Integer> progress) {
        List<MrpDailyPlanDetail> allDetails = new ArrayList<>();
        List<MrpUnallocatedQuantity> unallocated = new ArrayList<>();

        Map<String, List<MrpTerminalDemand>> grouped = new LinkedHashMap<>();
        for (MrpTerminalDemand demand : terminalDemands) {
            String key = demand.getRootMaterialCode() + "|" + demand.getTerminalMaterialCode() + "|"
                    + demand.getTerminalType() + "|" + demand.getSupplyMode() + "|"
                    + demand.getSourceMonth().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(demand);
        }

        int completedGroups = 0;
        for (Map.Entry<String, List<MrpTerminalDemand>> entry : grouped.entrySet()) {
            String groupKey = entry.getKey();
            distributeDemand(aggregate(entry.getValue()), groupKey, allDetails, unallocated);
            progress.accept(++completedGroups, grouped.size());
        }

        return new DistributionResult(allDetails, unallocated);
    }

    private MrpTerminalDemand aggregate(List<MrpTerminalDemand> demands) {
        MrpTerminalDemand first = demands.get(0);
        MrpTerminalDemand aggregate = new MrpTerminalDemand();
        aggregate.setRunId(first.getRunId());
        aggregate.setBatchId(first.getBatchId());
        aggregate.setFactoryCode(first.getFactoryCode());
        aggregate.setRootMaterialCode(first.getRootMaterialCode());
        aggregate.setTerminalMaterialCode(first.getTerminalMaterialCode());
        aggregate.setTerminalType(first.getTerminalType());
        aggregate.setSupplyMode(first.getSupplyMode());
        aggregate.setSourceMonth(first.getSourceMonth());
        aggregate.setQuantity(demands.stream().map(MrpTerminalDemand::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return aggregate;
    }

    private void distributeDemand(MrpTerminalDemand demand, String groupKey,
                                   List<MrpDailyPlanDetail> details, List<MrpUnallocatedQuantity> unallocated) {
        LocalDate sourceMonth = demand.getSourceMonth();
        LocalDate monthStart = sourceMonth.withDayOfMonth(1);
        LocalDate monthEnd = sourceMonth.withDayOfMonth(sourceMonth.lengthOfMonth());

        List<ProductionCalendarDay> productionDays = new ArrayList<>();
        for (LocalDate date = monthStart; !date.isAfter(monthEnd); date = date.plusDays(1)) {
            ProductionCalendarDay day = calendarMap.get(date);
            if (day != null && Boolean.TRUE.equals(day.getIsProductionDay())) {
                productionDays.add(day);
            }
        }

        if (productionDays.isEmpty()) {
            MrpUnallocatedQuantity unalloc = new MrpUnallocatedQuantity();
            unalloc.setRunId(runId);
            unalloc.setBatchId(batchId);
            unalloc.setDemandKey(groupKey);
            unalloc.setSourceDate(monthStart);
            unalloc.setAttemptedScheduleDate(monthStart.minusDays(LEAD_TIME_DAYS));
            unalloc.setQuantity(demand.getQuantity());
            unalloc.setReasonCode("NO_PRODUCTION_DAY");
            unalloc.setStatus("PENDING");
            unalloc.setRevision(0);
            unallocated.add(unalloc);
            return;
        }

        BigDecimal totalWeight = productionDays.stream()
                .map(this::effectiveWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalQuantity = demand.getQuantity();
        List<BigDecimal> allocations = distributeByWeight(totalQuantity, productionDays, totalWeight, "MACHINE_OUTPUT".equals(demand.getTerminalType()) ? 0 : 6);

        for (int i = 0; i < productionDays.size(); i++) {
            ProductionCalendarDay day = productionDays.get(i);
            BigDecimal quantity = allocations.get(i);
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            LocalDate sourceDate = day.getCalendarDate();
            LocalDate scheduleDate = sourceDate.minusDays(LEAD_TIME_DAYS);

            ProductionCalendarDay targetDay = calendarMap.get(scheduleDate);
            boolean isProductionDay = targetDay != null && Boolean.TRUE.equals(targetDay.getIsProductionDay());

            if (!isProductionDay) {
                MrpUnallocatedQuantity unalloc = new MrpUnallocatedQuantity();
                unalloc.setRunId(runId);
                unalloc.setBatchId(batchId);
                unalloc.setDemandKey(groupKey);
                unalloc.setSourceDate(sourceDate);
                unalloc.setAttemptedScheduleDate(scheduleDate);
                unalloc.setQuantity(quantity);
                unalloc.setReasonCode("NON_PRODUCTION_TARGET");
                unalloc.setStatus("PENDING");
                unalloc.setRevision(0);
                unallocated.add(unalloc);
                continue;
            }

            MrpDailyPlanDetail detail = new MrpDailyPlanDetail();
            detail.setRunId(runId);
            detail.setBatchId(batchId);
            detail.setFactoryCode(factoryCode);
            detail.setRootMaterialCode(demand.getRootMaterialCode());
            detail.setTerminalMaterialCode(demand.getTerminalMaterialCode());
            detail.setTerminalType(demand.getTerminalType());
            detail.setSupplyMode(demand.getSupplyMode());
            detail.setPathGroupKey(groupKey);
            detail.setSourceMonth(sourceMonth);
            detail.setSourceDate(sourceDate);
            detail.setScheduleDate(scheduleDate);
            detail.setSystemQuantity(quantity);
            detail.setIsProductionDay(isProductionDay);
            detail.setRevision(0);
            detail.setRowKey(computeRowKey(factoryCode, demand.getRootMaterialCode(),
                    demand.getTerminalMaterialCode(), demand.getTerminalType(), demand.getSupplyMode(),
                    groupKey, sourceMonth, scheduleDate));
            details.add(detail);
        }
    }

    private List<BigDecimal> distributeByWeight(BigDecimal totalQuantity,
                                                 List<ProductionCalendarDay> productionDays,
                                                 BigDecimal totalWeight, int scale) {
        if (totalQuantity.stripTrailingZeros().scale() > scale) throw new IllegalStateException("排产需求数量精度不合法：" + totalQuantity.toPlainString());
        List<BigDecimal> allocations = new ArrayList<>();
        List<WeightedIndex> weightedIndices = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;

        for (int i = 0; i < productionDays.size(); i++) {
            BigDecimal rawAllocation = totalQuantity.multiply(effectiveWeight(productionDays.get(i)))
                    .divide(totalWeight, 18, RoundingMode.HALF_UP);
            BigDecimal baseAllocation = rawAllocation.setScale(scale, RoundingMode.FLOOR);
            allocations.add(baseAllocation);
            allocated = allocated.add(baseAllocation);
            weightedIndices.add(new WeightedIndex(i, rawAllocation.subtract(baseAllocation)));
        }

        weightedIndices.sort((a, b) -> {
            int fractionalCompare = b.fractional.compareTo(a.fractional);
            return fractionalCompare != 0 ? fractionalCompare : Integer.compare(a.index, b.index);
        });

        // 余数按 0.000001 逐份补给余数最大的日期，保证合计严格等于需求。
        BigDecimal unit = BigDecimal.ONE.movePointLeft(scale);
        int unitsToAllocate = totalQuantity.subtract(allocated).divide(unit, 0, RoundingMode.UNNECESSARY).intValueExact();
        for (int i = 0; i < unitsToAllocate; i++) {
            int idx = weightedIndices.get(i % weightedIndices.size()).index;
            allocations.set(idx, allocations.get(idx).add(unit));
        }

        return allocations;
    }

    private String computeRowKey(String factoryCode, String rootMaterialCode, String terminalMaterialCode,
                                  String terminalType, String supplyMode, String pathGroupKey,
                                  LocalDate sourceMonth, LocalDate scheduleDate) {
        String raw = factoryCode + "|" + rootMaterialCode + "|" + terminalMaterialCode + "|"
                + terminalType + "|" + supplyMode + "|" + pathGroupKey + "|"
                + sourceMonth.format(DateTimeFormatter.ISO_LOCAL_DATE) + "|"
                + scheduleDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private BigDecimal effectiveWeight(ProductionCalendarDay day) {
        BigDecimal weight = day.getAllocationWeight();
        return weight != null && weight.compareTo(BigDecimal.ZERO) > 0 ? weight : BigDecimal.ONE;
    }

    private static class WeightedIndex {
        final int index;
        final BigDecimal fractional;

        WeightedIndex(int index, BigDecimal fractional) {
            this.index = index;
            this.fractional = fractional;
        }
    }

    public List<LocalDate> getScheduleDateRange(int weeks) {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusWeeks(weeks).plusDays(LEAD_TIME_DAYS);
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dates.add(d);
        }
        return dates;
    }

    public static class DistributionResult {
        public final List<MrpDailyPlanDetail> details;
        public final List<MrpUnallocatedQuantity> unallocated;

        public DistributionResult(List<MrpDailyPlanDetail> details, List<MrpUnallocatedQuantity> unallocated) {
            this.details = details;
            this.unallocated = unallocated;
        }
    }
}
