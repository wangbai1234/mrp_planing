package com.mrp.planning;

import com.mrp.planning.domain.PlanningCalculator;
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

    // === R002: 月拆周、余数前置 ===

    @Test
    void splitQuantities_900() {
        // 900 → carry=300, rest=600, base=200, remainder=0
        // ordinary=[200, 200, 200]
        long[] q = PlanningCalculator.splitQuantities(900);
        assertEquals(300, q[0]); // carry
        assertEquals(200, q[1]); // W1
        assertEquals(200, q[2]); // W2
        assertEquals(200, q[3]); // W3
        assertEquals(900, q[0] + q[1] + q[2] + q[3]); // conservation
    }

    @Test
    void splitQuantities_1000_remainderToFront() {
        // 1000 → carry=334, rest=666, base=222, remainder=0
        long[] q = PlanningCalculator.splitQuantities(1000);
        assertEquals(334, q[0]); // carry = ceil(1000/3) = 334
        assertEquals(222, q[1]); // W1
        assertEquals(222, q[2]); // W2
        assertEquals(222, q[3]); // W3
        assertEquals(1000, q[0] + q[1] + q[2] + q[3]);
    }

    @Test
    void splitQuantities_1200() {
        // 1200 → carry=400, rest=800, base=266, remainder=2
        // ordinary=[267, 267, 266]
        long[] q = PlanningCalculator.splitQuantities(1200);
        assertEquals(400, q[0]); // carry
        assertEquals(267, q[1]); // W1 (remainder >= 1)
        assertEquals(267, q[2]); // W2 (remainder >= 2)
        assertEquals(266, q[3]); // W3
        assertEquals(1200, q[0] + q[1] + q[2] + q[3]);
    }

    @Test
    void splitQuantities_zero() {
        long[] q = PlanningCalculator.splitQuantities(0);
        assertEquals(0, q[0]);
        assertEquals(0, q[1]);
        assertEquals(0, q[2]);
        assertEquals(0, q[3]);
    }

    @Test
    void splitQuantities_1() {
        // 1 → carry=1, rest=0, all ordinary=0
        long[] q = PlanningCalculator.splitQuantities(1);
        assertEquals(1, q[0]);
        assertEquals(0, q[1]);
        assertEquals(0, q[2]);
        assertEquals(0, q[3]);
    }

    @Test
    void splitQuantities_conservation() {
        // Conservation: sum of quantities == available
        for (long available : new long[]{0, 1, 5, 100, 999, 1000, 1200, 5000, 10000}) {
            long[] q = PlanningCalculator.splitQuantities(available);
            assertEquals(available, q[0] + q[1] + q[2] + q[3],
                    "Conservation failed for available=" + available);
        }
    }

    // === R003: 提前量来源月份 ===

    @Test
    void splitMonthToWeeks_aug2026() {
        YearMonth aug = YearMonth.of(2026, 8);
        LocalDate currentWeek = LocalDate.of(2026, 8, 3); // Monday

        var slots = PlanningCalculator.splitMonthToWeeks(aug, currentWeek);

        // Should have 4 slots: carry (prev month W4) + 3 ordinary
        assertEquals(4, slots.size());

        // Ordinary slots should be in August
        long ordinaryCount = slots.stream().filter(s -> !s.isCarry()).count();
        assertEquals(3, ordinaryCount);

        // Carry slot should be in July
        long carryCount = slots.stream().filter(s -> s.isCarry()).count();
        assertEquals(1, carryCount);

        // All source months should be August
        assertTrue(slots.stream().allMatch(s -> s.sourceMonth().getMonthValue() == 8));
    }

    // === R005: 12 周窗口 ===

    @Test
    void generate12WeekPlan_shouldProduce12Weeks() {
        LocalDate currentWeek = LocalDate.of(2026, 8, 3);
        List<YearMonth> months = List.of(
                YearMonth.of(2026, 8),
                YearMonth.of(2026, 9),
                YearMonth.of(2026, 10)
        );

        List<WeekPlan> plans = PlanningCalculator.generate12WeekPlan(
                3600, months, currentWeek, null, null, null);

        assertEquals(12, plans.size());

        // All week start dates should be Monday
        for (WeekPlan p : plans) {
            assertEquals(1, p.weekStartDate().getDayOfWeek().getValue(),
                    "Week " + p.weekStartDate() + " is not Monday");
        }

        // Conservation: sum of system quantities should equal total available
        long totalQty = plans.stream().mapToLong(WeekPlan::systemQuantity).sum();
        assertEquals(3600, totalQty, "Total quantity should be conserved");
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
        // Create plans with known quantities
        LocalDate currentWeek = LocalDate.of(2026, 8, 3);
        List<WeekPlan> plans = List.of(
                WeekPlan.auto(currentWeek, currentWeek, currentWeek, 1, false, false, 300),
                WeekPlan.auto(currentWeek.plusWeeks(1), currentWeek, currentWeek, 2, false, false, 500),
                WeekPlan.auto(currentWeek.plusWeeks(2), currentWeek, currentWeek, 3, false, false, 200)
        );

        // Capacity = 400
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
