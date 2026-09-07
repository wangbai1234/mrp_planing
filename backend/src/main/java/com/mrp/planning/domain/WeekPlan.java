package com.mrp.planning.domain;

import java.time.LocalDate;

public record WeekPlan(
        LocalDate weekStartDate,
        LocalDate physicalMonth,
        LocalDate sourceMonth,
        int slot,
        boolean isCarry,
        boolean isLocked,
        long systemQuantity,
        Long manualQuantity,
        long effectiveQuantity,
        boolean capacityExceeded,
        long capacityExcessQty
) {
    public static WeekPlan auto(LocalDate weekStartDate, LocalDate physicalMonth,
                                LocalDate sourceMonth, int slot, boolean isCarry,
                                boolean isLocked, long systemQuantity) {
        return new WeekPlan(weekStartDate, physicalMonth, sourceMonth, slot, isCarry,
                isLocked, systemQuantity, null, systemQuantity, false, 0);
    }

    public WeekPlan withManual(Long manualQty) {
        long effective = (manualQty != null) ? manualQty : this.systemQuantity;
        return new WeekPlan(weekStartDate, physicalMonth, sourceMonth, slot, isCarry,
                isLocked, systemQuantity, manualQty, effective, capacityExceeded, capacityExcessQty);
    }

    public WeekPlan withCapacity(boolean exceeded, long excessQty) {
        return new WeekPlan(weekStartDate, physicalMonth, sourceMonth, slot, isCarry,
                isLocked, systemQuantity, manualQuantity, effectiveQuantity, exceeded, excessQty);
    }
}
