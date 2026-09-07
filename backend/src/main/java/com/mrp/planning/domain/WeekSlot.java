package com.mrp.planning.domain;

import java.time.LocalDate;

public record WeekSlot(
        LocalDate weekStartDate,
        LocalDate physicalMonth,
        LocalDate sourceMonth,
        int slot,
        boolean isCarry
) {
    public WeekSlot {
        assert weekStartDate.getDayOfWeek().getValue() == 1 : "weekStartDate must be Monday";
    }
}
