package com.mrp.planning;

import com.mrp.planning.domain.PlanningCalculator;
import com.mrp.planning.domain.PlanningCalculator.MonthlyForecast;
import com.mrp.planning.domain.WeekPlan;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanningCalculatorTest {

    // === R001: 月可排量 MAX(0) ===

    @Test
    void monthlyAvailable_normal() {
        // forecast=1000, inventory=200, shipped=100 → available=700
        assertEquals(700, PlanningCalculator.monthlyAvailable(1000, 200, 100));
    }

    @Test
    void monthlyAvailable_inventoryExceedsDemand() {
        // forecast=500, inventory=600, shipped=0 → available=0
        assertEquals(0, PlanningCalculator.monthlyAvailable(500, 600, 0));
    }

    @Test
    void monthlyAvailable_negativeResultClampedToZero() {
        // forecast=100, inventory=200, shipped=300 → MAX(0, -400) = 0
        assertEquals(0, PlanningCalculator.monthlyAvailable(100, 200, 300));
    }

    // === R002: 月拆周、余数前置到第一周 ===
    // 需求文档规则：月可排量平均分到W1-W3，余数放到第一周

    @Test
    void splitQuantities_900() {
        // 900 / 3 = 300, remainder = 0 → W1=300, W2=300, W3=300
        long[] q = PlanningCalculator.splitQuantities(900);
        assertEquals(3, q.length);
        assertEquals(300, q[0]); // W1
        assertEquals(300, q[1]); // W2
        assertEquals(300, q[2]); // W3
        assertEquals(900, q[0] + q[1] + q[2]); // conservation
    }

    @Test
    void splitQuantities_1000_remainderToFront() {
        // 1000 / 3 = 333, remainder = 1 → W1=334, W2=333, W3=333
        long[] q = PlanningCalculator.splitQuantities(1000);
        assertEquals(3, q.length);
        assertEquals(334, q[0]); // W1 gets remainder
        assertEquals(333, q[1]); // W2
        assertEquals(333, q[2]); // W3
        assertEquals(1000, q[0] + q[1] + q[2]); // conservation
    }

    @Test
    void splitQuantities_1200() {
        // 1200 / 3 = 400, remainder = 0 → W1=400, W2=400, W3=400
        long[] q = PlanningCalculator.splitQuantities(1200);
        assertEquals(3, q.length);
        assertEquals(400, q[0]); // W1
        assertEquals(400, q[1]); // W2
        assertEquals(400, q[2]); // W3
        assertEquals(1200, q[0] + q[1] + q[2]);
    }

    @Test
    void splitQuantities_700() {
        // TC001: 700 / 3 = 233, remainder = 1 → W1=234, W2=233, W3=233
        // 但UAT期望 W1=233, W2=233, W3=234 (余数给最后一周?)
        // 需求文档说"余数放到第一周"，所以应该是 W1=234
        // 这里测试需求文档规则
        long[] q = PlanningCalculator.splitQuantities(700);
        assertEquals(3, q.length);
        assertEquals(234, q[0]); // W1 gets remainder
        assertEquals(233, q[1]); // W2
        assertEquals(233, q[2]); // W3
        assertEquals(700, q[0] + q[1] + q[2]);
    }

    @Test
    void splitQuantities_zero() {
        long[] q = PlanningCalculator.splitQuantities(0);
        assertEquals(3, q.length);
        assertEquals(0, q[0]);
        assertEquals(0, q[1]);
        assertEquals(0, q[2]);
    }

    @Test
    void splitQuantities_1() {
        // 1 / 3 = 0, remainder = 1 → W1=1, W2=0, W3=0
        long[] q = PlanningCalculator.splitQuantities(1);
        assertEquals(3, q.length);
        assertEquals(1, q[0]);
        assertEquals(0, q[1]);
        assertEquals(0, q[2]);
    }

    @Test
    void splitQuantities_2() {
        // 2 / 3 = 0, remainder = 2 → W1=1, W2=1, W3=0
        long[] q = PlanningCalculator.splitQuantities(2);
        assertEquals(3, q.length);
        assertEquals(1, q[0]);
        assertEquals(1, q[1]);
        assertEquals(0, q[2]);
    }

    @Test
    void splitQuantities_conservation() {
        // Conservation: sum of quantities == available
        for (long available : new long[]{0, 1, 2, 3, 5, 100, 999, 1000, 1200, 5000, 10000}) {
            long[] q = PlanningCalculator.splitQuantities(available);
            assertEquals(available, q[0] + q[1] + q[2],
                    "Conservation failed for available=" + available);
        }
    }

    // === R003: 提前一周滚动 ===

    @Test
    void splitMonthToWeeks_aug2026() {
        YearMonth aug = YearMonth.of(2026, 8);
        LocalDate currentWeek = LocalDate.of(2026, 8, 3); // Monday

        var slots = PlanningCalculator.splitMonthToWeeks(aug, currentWeek);

        // Should have 4 slots: 3 ordinary + 1 carry
        assertEquals(4, slots.size());

        // Ordinary slots should be in August
        long ordinaryCount = slots.stream().filter(s -> !s.isCarry()).count();
        assertEquals(3, ordinaryCount);

        // Carry slot should be in July
        long carryCount = slots.stream().filter(s -> s.isCarry()).count();
        assertEquals(1, carryCount);
    }

    // === R005: 12周窗口 + 提前一周滚动 ===

    @Test
    void generate12WeekPlan_shouldProduce12Weeks() {
        LocalDate currentWeek = LocalDate.of(2026, 8, 3);
        List<MonthlyForecast> forecasts = List.of(
                MonthlyForecast.of(YearMonth.of(2026, 8), 900, 0, 0),
                MonthlyForecast.of(YearMonth.of(2026, 9), 1200, 0, 0),
                MonthlyForecast.of(YearMonth.of(2026, 10), 1500, 0, 0)
        );

        List<WeekPlan> plans = PlanningCalculator.generate12WeekPlan(
                forecasts, currentWeek, null, null, null);

        assertEquals(12, plans.size());

        // All week start dates should be Monday
        for (WeekPlan p : plans) {
            assertEquals(1, p.weekStartDate().getDayOfWeek().getValue(),
                    "Week " + p.weekStartDate() + " is not Monday");
        }

        // Conservation: sum of system quantities should equal total available
        // Total available = 900 + 1200 + 1500 = 3600
        long totalQty = plans.stream().mapToLong(WeekPlan::systemQuantity).sum();
        assertEquals(3600, totalQty, "Total quantity should be conserved");
    }

    @Test
    void generate12WeekPlan_carryFromNextMonth() {
        // 验证提前一周滚动：下月W1排入上月W4
        LocalDate currentWeek = LocalDate.of(2026, 8, 3);
        List<MonthlyForecast> forecasts = List.of(
                MonthlyForecast.of(YearMonth.of(2026, 8), 900, 0, 0),
                MonthlyForecast.of(YearMonth.of(2026, 9), 1200, 0, 0)
        );

        List<WeekPlan> plans = PlanningCalculator.generate12WeekPlan(
                forecasts, currentWeek, null, null, null);

        // Aug has 3 ordinary + 1 carry (Sep W1)
        // Sep has 3 ordinary + 1 carry (Oct W1 if exists)
        // Total from Aug: 300(W1) + 300(W2) + 300(W3) + carry(Sep W1=400) = 1300
        // But carry is from Sep, so Aug's own contribution is 900

        // Find carry week in August
        WeekPlan augCarry = plans.stream()
                .filter(p -> p.isCarry() && p.physicalMonth().getMonthValue() == 8)
                .findFirst().orElse(null);
        assertNotNull(augCarry, "Should have carry week in August");
        assertEquals(400, augCarry.systemQuantity(), "Carry should be Sep's W1 (400)");
    }

    @Test
    void generate12WeekPlan_lockedWeeks() {
        // 验证已执行周被锁定
        // Aug 2026: Mon=Jul27, so weeks are Jul27, Aug3, Aug10, Aug17
        // Set currentWeek = Aug10, so Jul27 and Aug3 are locked, Aug10 is not
        LocalDate currentWeek = LocalDate.of(2026, 8, 10); // Monday Aug10
        List<MonthlyForecast> forecasts = List.of(
                MonthlyForecast.of(YearMonth.of(2026, 8), 900, 0, 0),
                MonthlyForecast.of(YearMonth.of(2026, 9), 900, 0, 0)
        );

        List<WeekPlan> plans = PlanningCalculator.generate12WeekPlan(
                forecasts, currentWeek, null, null, null);

        // W1 (Jul27) should be locked (before currentWeek Aug10)
        WeekPlan w1 = plans.get(0);
        assertTrue(w1.isLocked(), "W1 (Jul27) should be locked");

        // W2 (Aug3) should be locked (before currentWeek Aug10)
        WeekPlan w2 = plans.get(1);
        assertTrue(w2.isLocked(), "W2 (Aug3) should be locked");

        // W3 (Aug10) should NOT be locked (same as currentWeek)
        WeekPlan w3 = plans.get(2);
        assertFalse(w3.isLocked(), "W3 (Aug10) should not be locked");
    }

    // === R001: 月可排量 MAX(0) - 边界测试 ===

    @Test
    void monthlyAvailable_exactZero() {
        // forecast=1000, inventory=500, shipped=500 → available=0
        assertEquals(0, PlanningCalculator.monthlyAvailable(1000, 500, 500));
    }

    @Test
    void monthlyAvailable_allZero() {
        assertEquals(0, PlanningCalculator.monthlyAvailable(0, 0, 0));
    }

    @Test
    void monthlyAvailable_largeNumbers() {
        // 大数值测试
        assertEquals(999999, PlanningCalculator.monthlyAvailable(1000000, 1, 0));
    }

    // === R002: 月拆周 - 边界测试 ===

    @Test
    void splitQuantities_3() {
        // 3 / 3 = 1, remainder = 0
        long[] q = PlanningCalculator.splitQuantities(3);
        assertEquals(1, q[0]);
        assertEquals(1, q[1]);
        assertEquals(1, q[2]);
    }

    @Test
    void splitQuantities_4() {
        // 4 / 3 = 1, remainder = 1 → W1=2, W2=1, W3=1
        long[] q = PlanningCalculator.splitQuantities(4);
        assertEquals(2, q[0]);
        assertEquals(1, q[1]);
        assertEquals(1, q[2]);
        assertEquals(4, q[0] + q[1] + q[2]);
    }

    @Test
    void splitQuantities_maxLong() {
        // 测试大数值不溢出
        long available = Long.MAX_VALUE / 3;
        long[] q = PlanningCalculator.splitQuantities(available);
        assertEquals(available, q[0] + q[1] + q[2]);
    }

    // === R005: 12周窗口 - 边界测试 ===

    @Test
    void generate12WeekPlan_singleMonth() {
        // 只有1个月的数据，每月产生4周(W1-W3+W4=0)
        LocalDate currentWeek = LocalDate.of(2026, 8, 3);
        List<MonthlyForecast> forecasts = List.of(
                MonthlyForecast.of(YearMonth.of(2026, 8), 900, 0, 0)
        );
        List<WeekPlan> plans = PlanningCalculator.generate12WeekPlan(
                forecasts, currentWeek, null, null, null);
        // 单月只产生4周（不足12周时不会填充）
        assertEquals(4, plans.size());
    }

    @Test
    void generate12WeekPlan_sixMonths() {
        // 6个月数据 - 12周窗口覆盖Aug+Sep+Oct+部分Nov（因为carry机制）
        // Aug W4 = Sep W1, Sep W4 = Oct W1, Oct W4 = Nov W1
        LocalDate currentWeek = LocalDate.of(2026, 8, 3);
        List<MonthlyForecast> forecasts = List.of(
                MonthlyForecast.of(YearMonth.of(2026, 8), 900, 0, 0),
                MonthlyForecast.of(YearMonth.of(2026, 9), 1200, 0, 0),
                MonthlyForecast.of(YearMonth.of(2026, 10), 1500, 0, 0),
                MonthlyForecast.of(YearMonth.of(2026, 11), 1800, 0, 0),
                MonthlyForecast.of(YearMonth.of(2026, 12), 2100, 0, 0),
                MonthlyForecast.of(YearMonth.of(2027, 1), 2400, 0, 0)
        );
        List<WeekPlan> plans = PlanningCalculator.generate12WeekPlan(
                forecasts, currentWeek, null, null, null);
        assertEquals(12, plans.size());
        // 总量守恒：验证所有12周的总量
        long total = plans.stream().mapToLong(WeekPlan::systemQuantity).sum();
        // Aug(900) + Sep(1200) + Oct(1500) + Nov carry(600) = 4200
        assertEquals(4200, total);
    }

    // === R013: 负值归零 ===

    @Test
    void monthlyAvailable_allNegativeCasesClampToZero() {
        assertEquals(0, PlanningCalculator.monthlyAvailable(0, 0, 0));
        assertEquals(0, PlanningCalculator.monthlyAvailable(0, 100, 0));
        assertEquals(0, PlanningCalculator.monthlyAvailable(100, 200, 0));
        assertEquals(0, PlanningCalculator.monthlyAvailable(50, 0, 100));
    }

    // === R014: 产能超限 ===

    @Test
    void checkCapacity_shouldMarkExceeded() {
        LocalDate currentWeek = LocalDate.of(2026, 8, 3);
        List<WeekPlan> plans = List.of(
                WeekPlan.auto(currentWeek, currentWeek, currentWeek, 1, false, false, 300),
                WeekPlan.auto(currentWeek.plusWeeks(1), currentWeek, currentWeek, 2, false, false, 500),
                WeekPlan.auto(currentWeek.plusWeeks(2), currentWeek, currentWeek, 3, false, false, 200)
        );

        List<WeekPlan> checked = PlanningCalculator.checkCapacity(plans, 400);

        assertFalse(checked.get(0).capacityExceeded()); // 300 <= 400
        assertTrue(checked.get(1).capacityExceeded());   // 500 > 400
        assertEquals(100, checked.get(1).capacityExcessQty()); // 500 - 400 = 100
        assertFalse(checked.get(2).capacityExceeded()); // 200 <= 400
    }

    // === Checksum 可重复性 ===

    @Test
    void computeInputChecksum_shouldBeReproducible() {
        String c1 = PlanningCalculator.computeInputChecksum(1L, 2L, 3L, 4L, "v1", LocalDate.of(2026, 8, 3));
        String c2 = PlanningCalculator.computeInputChecksum(1L, 2L, 3L, 4L, "v1", LocalDate.of(2026, 8, 3));
        assertEquals(c1, c2, "Same inputs should produce same checksum");
    }

    @Test
    void computeInputChecksum_differentInputsDifferentChecksum() {
        String c1 = PlanningCalculator.computeInputChecksum(1L, 2L, 3L, 4L, "v1", LocalDate.of(2026, 8, 3));
        String c2 = PlanningCalculator.computeInputChecksum(1L, 2L, 3L, 4L, "v1", LocalDate.of(2026, 8, 10));
        assertNotEquals(c1, c2);
    }
}
