package com.mrp.mrp.engine;

import com.mrp.mrp.domain.MrpDailyPlanDetail;
import com.mrp.mrp.domain.MrpTerminalDemand;
import com.mrp.mrp.domain.MrpUnallocatedQuantity;
import com.mrp.mrp.domain.ProductionCalendarDay;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyDistributionEngineTest {

    @Test void wholeMachineAllocationUsesIntegerRemainderAndExistingMinusSevenDayRule() {
        var output = demand("10", LocalDate.of(2026,10,1)); output.setTerminalType("MACHINE_OUTPUT");
        output.setTerminalMaterialCode(output.getRootMaterialCode());
        var calendar = List.of(day(LocalDate.of(2026,9,24),true,"1"), day(LocalDate.of(2026,9,25),true,"1"),
                day(LocalDate.of(2026,9,26),true,"1"), day(LocalDate.of(2026,10,1),true,"1"),
                day(LocalDate.of(2026,10,2),true,"1"), day(LocalDate.of(2026,10,3),true,"1"));
        var result = engine(calendar).distribute(List.of(output));
        assertEquals(List.of(new BigDecimal("4"),new BigDecimal("3"),new BigDecimal("3")),
                result.details.stream().map(MrpDailyPlanDetail::getSystemQuantity).toList());
        assertEquals(new BigDecimal("10"), sumPlans(result.details));
        assertTrue(result.details.stream().allMatch(plan -> plan.getScheduleDate().equals(plan.getSourceDate().minusDays(7))));
        assertEquals(LocalDate.of(2026,9,24),result.details.getFirst().getScheduleDate());
    }

    @Test void fractionalWholeMachineDemandFailsExplicitly() {
        var output = demand("10.5", LocalDate.of(2026,10,1)); output.setTerminalType("MACHINE_OUTPUT");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> engine(weekdayCalendar(
                LocalDate.of(2026,9,24), LocalDate.of(2026,10,31))).distribute(List.of(output)));
    }

    @Test
    void preservesDemandAcrossDailyPlansAndUnallocatedPoolWhenTargetCalendarIsMissing() {
        MrpTerminalDemand demand = demand("500.000000", LocalDate.of(2026, 10, 1));
        List<ProductionCalendarDay> octoberOnly = weekdayCalendar(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        DailyDistributionEngine.DistributionResult result =
                engine(octoberOnly).distribute(List.of(demand));

        assertEquals(17, result.details.size());
        assertEquals(5, result.unallocated.size());
        assertEquals(new BigDecimal("500.000000"),
                sumPlans(result.details).add(sumUnallocated(result.unallocated)));
        assertTrue(result.unallocated.stream()
                .allMatch(item -> "NON_PRODUCTION_TARGET".equals(item.getReasonCode())));
        assertEquals(LocalDate.of(2026, 10, 30),
                result.details.get(result.details.size() - 1).getSourceDate());
        assertEquals(LocalDate.of(2026, 10, 23),
                result.details.get(result.details.size() - 1).getScheduleDate());
    }

    @Test
    void createsAllTwentyTwoPlansWhenCalendarCoversSourceAndTargetDates() {
        MrpTerminalDemand demand = demand("500.000000", LocalDate.of(2026, 10, 1));
        List<ProductionCalendarDay> calendar = weekdayCalendar(
                LocalDate.of(2026, 9, 24), LocalDate.of(2026, 10, 31));

        DailyDistributionEngine.DistributionResult result =
                engine(calendar).distribute(List.of(demand));

        assertEquals(22, result.details.size());
        assertTrue(result.unallocated.isEmpty());
        assertEquals(new BigDecimal("500.000000"), sumPlans(result.details));
        assertTrue(result.details.stream().allMatch(plan ->
                plan.getScheduleDate().equals(plan.getSourceDate().minusDays(7))));
    }

    @Test
    void appliesLargestRemainderByWeightAndKeepsTheTotalExact() {
        MrpTerminalDemand demand = demand("10.00", LocalDate.of(2026, 10, 1));
        List<ProductionCalendarDay> calendar = new ArrayList<>();
        calendar.add(day(LocalDate.of(2026, 9, 24), true, "1"));
        calendar.add(day(LocalDate.of(2026, 9, 25), true, "1"));
        calendar.add(day(LocalDate.of(2026, 9, 26), true, "1"));
        calendar.add(day(LocalDate.of(2026, 10, 1), true, "1"));
        calendar.add(day(LocalDate.of(2026, 10, 2), true, "2"));
        calendar.add(day(LocalDate.of(2026, 10, 3), true, "3"));

        DailyDistributionEngine.DistributionResult result =
                engine(calendar).distribute(List.of(demand));

        assertEquals(List.of(new BigDecimal("1.666667"), new BigDecimal("3.333333"), new BigDecimal("5.000000")),
                result.details.stream().map(MrpDailyPlanDetail::getSystemQuantity).toList());
        assertEquals(new BigDecimal("10.000000"), sumPlans(result.details));
    }

    @Test
    void aggregatesDuplicateBusinessKeysBeforeCreatingUniqueDailyFacts() {
        MrpTerminalDemand first = demand("300.000000", LocalDate.of(2026, 10, 1));
        MrpTerminalDemand second = demand("200.000000", LocalDate.of(2026, 10, 1));
        List<ProductionCalendarDay> calendar = weekdayCalendar(
                LocalDate.of(2026, 9, 24), LocalDate.of(2026, 10, 31));

        DailyDistributionEngine.DistributionResult result =
                engine(calendar).distribute(List.of(first, second));

        assertEquals(22, result.details.size());
        assertTrue(result.unallocated.isEmpty());
        assertEquals(new BigDecimal("500.000000"), sumPlans(result.details));
        assertEquals(22, result.details.stream().map(MrpDailyPlanDetail::getRowKey).distinct().count());
    }

    private DailyDistributionEngine engine(List<ProductionCalendarDay> calendar) {
        return new DailyDistributionEngine(calendar, 21L, 35L, "F001", Map.of());
    }

    private MrpTerminalDemand demand(String quantity, LocalDate sourceMonth) {
        MrpTerminalDemand demand = new MrpTerminalDemand();
        demand.setRunId(21L);
        demand.setBatchId(35L);
        demand.setFactoryCode("F001");
        demand.setRootMaterialCode("ROOT-001");
        demand.setTerminalMaterialCode("T-001");
        demand.setTerminalType("RAW_MATERIAL");
        demand.setSupplyMode("PURCHASED");
        demand.setSourceMonth(sourceMonth.withDayOfMonth(1));
        demand.setQuantity(new BigDecimal(quantity));
        return demand;
    }

    private List<ProductionCalendarDay> weekdayCalendar(LocalDate start, LocalDate end) {
        List<ProductionCalendarDay> result = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            boolean weekday = date.getDayOfWeek() != DayOfWeek.SATURDAY
                    && date.getDayOfWeek() != DayOfWeek.SUNDAY;
            result.add(day(date, weekday, "1"));
        }
        return result;
    }

    private ProductionCalendarDay day(LocalDate date, boolean productionDay, String weight) {
        ProductionCalendarDay day = new ProductionCalendarDay();
        day.setFactoryCode("F001");
        day.setCalendarDate(date);
        day.setIsProductionDay(productionDay);
        day.setAllocationWeight(new BigDecimal(weight));
        return day;
    }

    private BigDecimal sumPlans(List<MrpDailyPlanDetail> plans) {
        return plans.stream().map(MrpDailyPlanDetail::getSystemQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumUnallocated(List<MrpUnallocatedQuantity> unallocated) {
        return unallocated.stream().map(MrpUnallocatedQuantity::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
