package com.mrp.planning.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class WeekPlanTest {

    @Test
    void auto_shouldCreateWithSystemQuantity() {
        LocalDate weekStart = LocalDate.of(2026, 8, 3);
        LocalDate physicalMonth = LocalDate.of(2026, 8, 1);
        LocalDate sourceMonth = LocalDate.of(2026, 8, 1);

        WeekPlan plan = WeekPlan.auto(weekStart, physicalMonth, sourceMonth, 1, false, false, 300);

        assertEquals(300, plan.systemQuantity());
        assertNull(plan.manualQuantity());
        assertEquals(300, plan.effectiveQuantity());
        assertFalse(plan.capacityExceeded());
        assertEquals(0, plan.capacityExcessQty());
    }

    @Test
    void withManual_shouldOverrideEffectiveQuantity() {
        LocalDate weekStart = LocalDate.of(2026, 8, 3);
        WeekPlan plan = WeekPlan.auto(weekStart, weekStart, weekStart, 1, false, false, 300);

        WeekPlan withManual = plan.withManual(500L);

        assertEquals(300, withManual.systemQuantity(), "system quantity should not change");
        assertEquals(500L, withManual.manualQuantity());
        assertEquals(500, withManual.effectiveQuantity(), "effective should use manual");
    }

    @Test
    void withManual_nullShouldKeepSystemQuantity() {
        LocalDate weekStart = LocalDate.of(2026, 8, 3);
        WeekPlan plan = WeekPlan.auto(weekStart, weekStart, weekStart, 1, false, false, 300);

        WeekPlan withNull = plan.withManual(null);

        assertEquals(300, withNull.systemQuantity());
        assertNull(withNull.manualQuantity());
        assertEquals(300, withNull.effectiveQuantity());
    }

    @Test
    void withCapacity_shouldMarkExceeded() {
        LocalDate weekStart = LocalDate.of(2026, 8, 3);
        WeekPlan plan = WeekPlan.auto(weekStart, weekStart, weekStart, 1, false, false, 500);

        WeekPlan exceeded = plan.withCapacity(true, 100);

        assertTrue(exceeded.capacityExceeded());
        assertEquals(100, exceeded.capacityExcessQty());
    }

    @Test
    void withCapacity_shouldMarkNotExceeded() {
        LocalDate weekStart = LocalDate.of(2026, 8, 3);
        WeekPlan plan = WeekPlan.auto(weekStart, weekStart, weekStart, 1, false, false, 300);

        WeekPlan ok = plan.withCapacity(false, 0);

        assertFalse(ok.capacityExceeded());
        assertEquals(0, ok.capacityExcessQty());
    }

    @Test
    void isCarry_shouldBeTrueForCarryWeek() {
        LocalDate weekStart = LocalDate.of(2026, 8, 3);
        WeekPlan carry = WeekPlan.auto(weekStart, weekStart, weekStart, 4, true, false, 400);

        assertTrue(carry.isCarry());
        assertEquals(4, carry.slot());
    }

    @Test
    void isLocked_shouldBeTrueForLockedWeek() {
        LocalDate weekStart = LocalDate.of(2026, 8, 3);
        WeekPlan locked = WeekPlan.auto(weekStart, weekStart, weekStart, 1, false, true, 300);

        assertTrue(locked.isLocked());
    }
}
