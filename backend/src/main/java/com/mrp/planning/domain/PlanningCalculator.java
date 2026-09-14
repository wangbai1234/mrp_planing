package com.mrp.planning.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class PlanningCalculator {

    private PlanningCalculator() {}

    /**
     * 月度预测数据，用于12周排产。
     */
    public record MonthlyForecast(YearMonth month, long forecast, long inventory, long shipped, long available) {
        public static MonthlyForecast of(YearMonth month, long forecast, long inventory, long shipped) {
            return new MonthlyForecast(month, forecast, inventory, shipped, monthlyAvailable(forecast, inventory, shipped));
        }
    }

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
     * 计算拆分数量。余数前置到第一周。
     * 将 available 平均分到3周，余数加到前面的周。
     * 返回 [W1, W2, W3]，不含 carry。
     */
    public static long[] splitQuantities(long available) {
        if (available <= 0) return new long[]{0, 0, 0};

        long base = available / 3;
        long remainder = available % 3;

        long w1 = base + (remainder >= 1 ? 1 : 0);
        long w2 = base + (remainder >= 2 ? 1 : 0);
        long w3 = base;

        return new long[]{w1, w2, w3};
    }

    /**
     * 为多个来源月份生成12周排产计划。
     * 从 currentWeekStart 开始，输出连续12周。
     * 月拆周规则（需求文档4.2）：月可排量平均分到W1-W3，余数前置到W1，W4为空。
     * 提前一周滚动（需求文档4.3）：下月W1排入上月W4位置。
     * 总量守恒：sum(12周) = sum(各月available)。
     *
     * 实现：每月4周。W1-W3来自本月available，W4=下月W1（carry）。
     * 当某月W1被carry到上月W4时，该月W1设为0避免重复。
     */
    public static List<WeekPlan> generate12WeekPlan(
            List<MonthlyForecast> monthlyForecasts,
            LocalDate currentWeekStart,
            List<Long> existingManualOverrides,
            List<Boolean> lockedWeeks,
            List<Long> weeklyCapacities
    ) {
        List<long[]> monthSplits = new ArrayList<>();
        for (MonthlyForecast mf : monthlyForecasts) {
            monthSplits.add(splitQuantities(mf.available()));
        }

        // Track which months have W1 carried into previous month's W4
        boolean[] w1CarriedOut = new boolean[monthlyForecasts.size()];
        for (int m = 0; m < monthlyForecasts.size() - 1; m++) {
            w1CarriedOut[m + 1] = true; // month (m+1)'s W1 goes to month m's W4
        }

        List<WeekPlan> result = new ArrayList<>();

        for (int m = 0; m < monthlyForecasts.size() && result.size() < 12; m++) {
            MonthlyForecast mf = monthlyForecasts.get(m);
            YearMonth ym = mf.month();
            long[] quantities = monthSplits.get(m);
            LocalDate firstDay = ym.atDay(1);
            LocalDate w1Start = firstOfMonday(firstDay);

            // W1: if this month's W1 is carried out, set to0
            long w1Qty = w1CarriedOut[m] ? 0 : quantities[0];
            if (result.size() < 12) {
                LocalDate weekStart = w1Start;
                boolean locked = isWeekLocked(weekStart, currentWeekStart);
                Long manual = getManual(existingManualOverrides, result.size());
                result.add(WeekPlan.auto(weekStart, firstDay, firstDay, 1, false, locked, w1Qty)
                        .withManual(manual));
            }

            // W2, W3
            for (int s = 1; s < 3 && result.size() < 12; s++) {
                LocalDate weekStart = w1Start.plusWeeks(s);
                boolean locked = isWeekLocked(weekStart, currentWeekStart);
                Long manual = getManual(existingManualOverrides, result.size());
                result.add(WeekPlan.auto(weekStart, firstDay, firstDay, s + 1, false, locked, quantities[s])
                        .withManual(manual));
            }

            // W4: carry from next month's W1, or0 for last month
            if (result.size() < 12) {
                LocalDate w4Start = w1Start.plusWeeks(3);
                boolean locked = isWeekLocked(w4Start, currentWeekStart);
                Long manual = getManual(existingManualOverrides, result.size());
                long w4Qty = 0;
                boolean w4IsCarry = false;
                YearMonth w4SourceMonth = ym;
                if (m + 1 < monthlyForecasts.size()) {
                    w4Qty = monthSplits.get(m + 1)[0]; // next month's W1
                    w4SourceMonth = monthlyForecasts.get(m + 1).month();
                    w4IsCarry = true;
                }
                result.add(WeekPlan.auto(w4Start, firstDay, w4SourceMonth.atDay(1), 4, w4IsCarry, locked, w4Qty)
                        .withManual(manual));
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
