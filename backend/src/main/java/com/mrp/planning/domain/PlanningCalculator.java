package com.mrp.planning.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class PlanningCalculator {

    private PlanningCalculator() {}

    /**
     * 月可排量 = MAX(0, forecast - inventory - shipped)
     */
    public static long monthlyAvailable(long forecast, long inventory, long shipped) {
        return Math.max(0, forecast - inventory - shipped);
    }

    /**
     * 月份拆分和提前量。
     * carry = ceil(available / 3)
     * rest = available - carry
     * base = floor(rest / 3)
     * remainder = rest % 3
     * ordinary = [base + (remainder >= 1 ? 1 : 0), base + (remainder >= 2 ? 1 : 0), base]
     * carry 放在上月物理第 4 周，来源月份仍是本月。
     * ordinary 放在本月 W1、W2、W3。
     */
    public static List<WeekSlot> splitMonthToWeeks(YearMonth sourceMonth, LocalDate currentWeekStart) {
        List<WeekSlot> slots = new ArrayList<>();

        // W1, W2, W3 of source month (ordinary weeks)
        LocalDate firstDay = sourceMonth.atDay(1);
        LocalDate w1 = firstOfMonday(firstDay);
        LocalDate w2 = w1.plusWeeks(1);
        LocalDate w3 = w2.plusWeeks(1);

        slots.add(new WeekSlot(w1, sourceMonth.atDay(1), sourceMonth.atDay(1), 1, false));
        slots.add(new WeekSlot(w2, sourceMonth.atDay(1), sourceMonth.atDay(1), 2, false));
        slots.add(new WeekSlot(w3, sourceMonth.atDay(1), sourceMonth.atDay(1), 3, false));

        // Carry: placed in previous month's W4, source is current month
        YearMonth prevMonth = sourceMonth.minusMonths(1);
        LocalDate prevW4 = w1.minusWeeks(1);
        slots.add(new WeekSlot(prevW4, prevMonth.atDay(1), sourceMonth.atDay(1), 0, true));

        return slots;
    }

    /**
     * 计算拆分数量。余数前置。
     * carry = ceil(available / 3)
     * rest = available - carry
     * base = floor(rest / 3)
     * remainder = rest % 3
     */
    public static long[] splitQuantities(long available) {
        if (available <= 0) return new long[]{0, 0, 0, 0};

        long carry = (available + 2) / 3; // ceil(available/3)
        long rest = available - carry;
        long base = rest / 3;
        long remainder = rest % 3;

        long w1 = base + (remainder >= 1 ? 1 : 0);
        long w2 = base + (remainder >= 2 ? 1 : 0);
        long w3 = base;

        return new long[]{carry, w1, w2, w3};
    }

    /**
     * 为多个来源月份生成 12 周排产计划。
     * 从 currentWeekStart 开始，输出连续 12 周。
     */
    public static List<WeekPlan> generate12WeekPlan(
            long available,
            List<YearMonth> sourceMonths,
            LocalDate currentWeekStart,
            List<Long> existingManualOverrides,
            List<Boolean> lockedWeeks,
            List<Long> weeklyCapacities
    ) {
        List<WeekPlan> plans = new ArrayList<>();

        // Generate week slots for each source month
        List<WeekSlot> allSlots = new ArrayList<>();
        for (YearMonth ym : sourceMonths) {
            allSlots.addAll(splitMonthToWeeks(ym, currentWeekStart));
        }

        // Sort by week start date and take first 12
        allSlots.sort((a, b) -> a.weekStartDate().compareTo(b.weekStartDate()));

        // Calculate quantities per source month
        long perMonth = sourceMonths.isEmpty() ? 0 : available / sourceMonths.size();
        // For simplicity, distribute available evenly across months
        // In real scenario, each month has its own forecast/inventory/shipped

        List<WeekPlan> result = new ArrayList<>();
        int weekIndex = 0;
        for (int m = 0; m < sourceMonths.size() && result.size() < 12; m++) {
            YearMonth ym = sourceMonths.get(m);
            long[] quantities = splitQuantities(perMonth);

            // Carry (slot 0) - placed in previous month's W4
            if (result.size() < 12) {
                LocalDate prevW4 = ym.atDay(1).minusWeeks(1);
                LocalDate monPrevW4 = firstOfMonday(prevW4);
                boolean locked = isWeekLocked(monPrevW4, currentWeekStart);
                Long manual = getManual(existingManualOverrides, weekIndex);
                result.add(WeekPlan.auto(monPrevW4, ym.minusMonths(1).atDay(1), ym.atDay(1), 0, true, locked, quantities[0])
                        .withManual(manual));
                weekIndex++;
            }

            // Ordinary weeks (slots 1, 2, 3)
            for (int s = 1; s <= 3 && result.size() < 12; s++) {
                LocalDate weekStart = ym.atDay(1).plusWeeks(s - 1);
                LocalDate monday = firstOfMonday(weekStart);
                boolean locked = isWeekLocked(monday, currentWeekStart);
                Long manual = getManual(existingManualOverrides, weekIndex);
                result.add(WeekPlan.auto(monday, ym.atDay(1), ym.atDay(1), s, false, locked, quantities[s])
                        .withManual(manual));
                weekIndex++;
            }
        }

        return result;
    }

    /**
     * 产能超限校验。
     */
    public static List<WeekPlan> checkCapacity(List<WeekPlan> plans, long weeklyCapacity) {
        return plans.stream()
                .map(p -> {
                    if (p.effectiveQuantity() > weeklyCapacity) {
                        return p.withCapacity(true, p.effectiveQuantity() - weeklyCapacity);
                    }
                    return p.withCapacity(false, 0);
                })
                .toList();
    }

    /**
     * 计算输入 checksum 用于可重复性验证。
     */
    public static String computeInputChecksum(Long forecastVersionId, Long inventorySnapshotId,
                                               Long shipmentBatchId, Long capacityVersionId,
                                               String ruleVersion, LocalDate currentWeekStart) {
        return String.join(":",
                String.valueOf(forecastVersionId),
                String.valueOf(inventorySnapshotId),
                String.valueOf(shipmentBatchId),
                String.valueOf(capacityVersionId),
                ruleVersion,
                currentWeekStart.toString()
        );
    }

    /**
     * 计算结果 checksum。
     */
    public static String computeResultChecksum(List<WeekPlan> plans) {
        StringBuilder sb = new StringBuilder();
        for (WeekPlan p : plans) {
            sb.append(p.weekStartDate()).append("=").append(p.systemQuantity()).append(";");
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    // --- Helpers ---

    private static LocalDate firstOfMonday(LocalDate date) {
        LocalDate d = date;
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.minusDays(1);
        }
        return d;
    }

    private static boolean isWeekLocked(LocalDate weekStart, LocalDate currentWeekStart) {
        return weekStart.isBefore(currentWeekStart);
    }

    private static Long getManual(List<Long> overrides, int index) {
        if (overrides == null || index >= overrides.size()) return null;
        return overrides.get(index);
    }
}
