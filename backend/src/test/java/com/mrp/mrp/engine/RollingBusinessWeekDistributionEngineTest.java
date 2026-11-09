package com.mrp.mrp.engine;

import com.mrp.mrp.domain.BusinessWeekDetail;
import com.mrp.mrp.domain.MrpDailyPlanDetail;
import com.mrp.mrp.domain.MrpTerminalDemand;
import com.mrp.mrp.domain.MrpUnallocatedQuantity;
import com.mrp.mrp.domain.ProductionCalendarDay;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollingBusinessWeekDistributionEngineTest {
    @Test
    void movesFirstThirdToPreviousLastWeekAndKeepsRemainderInFirstThird() {
        var result = engine(weeks()).distribute(List.of(demand("1001", "MACHINE_OUTPUT")));

        assertTrue(result.unallocated.isEmpty());
        assertQuantity("335", sum(result.details, "2026-10-26", "2026-10-30"));
        assertQuantity("333", sum(result.details, "2026-11-02", "2026-11-06"));
        assertQuantity("333", sum(result.details, "2026-11-09", "2026-11-13"));
        assertEquals(LocalDate.of(2026, 10, 26), result.details.getFirst().getScheduleDate());
        assertTrue(result.details.stream().allMatch(detail -> detail.getSourceDate().equals(detail.getScheduleDate())));
        assertTrue(result.details.stream().allMatch(detail -> detail.getSourceMonth().equals(LocalDate.of(2026, 11, 1))));
        assertTrue(result.details.stream().allMatch(detail -> detail.getSystemQuantity().stripTrailingZeros().scale() <= 0));
    }

    @Test
    void distributesNetFiveHundredAs168166166ThenDaily3434343333() {
        var result = engine(weeks()).distribute(List.of(demand("500.000000", "MACHINE_OUTPUT")));

        assertQuantity("168", sum(result.details, "2026-10-26", "2026-10-30"));
        assertEquals(List.of("34", "34", "34", "33", "33"), result.details.stream()
                .filter(detail -> detail.getScheduleDate().getMonthValue() == 10)
                .map(detail -> detail.getSystemQuantity().stripTrailingZeros().toPlainString()).toList());
        assertQuantity("166", sum(result.details, "2026-11-02", "2026-11-06"));
        assertQuantity("166", sum(result.details, "2026-11-09", "2026-11-13"));
        assertConserved("500", result);
    }

    @Test
    void ignoresCapacityAndAllocationWeightsAndUsesProductionDaysOnly() {
        var calendar = calendar();
        calendar.getFirst().setAllocationWeight(new BigDecimal("999"));
        calendar.getFirst().setDailyCapacity(BigDecimal.ONE);
        calendar.get(1).setIsProductionDay(false);
        var result = new RollingBusinessWeekDistributionEngine(calendar, weeks(), 7L, 9L, "爱培科")
                .distribute(List.of(demand("500", "RAW_MATERIAL")));

        assertEquals(List.of("42", "42", "42", "42"), result.details.stream()
                .filter(detail -> detail.getScheduleDate().getMonthValue() == 10)
                .map(detail -> detail.getSystemQuantity().toPlainString()).toList());
        assertTrue(result.details.stream().noneMatch(detail -> detail.getScheduleDate().equals(LocalDate.of(2026, 10, 27))));
        assertConserved("500", result);
    }

    @Test
    void fractionalTerminalDemandRetainsSixDigitPrecisionAndExactTotal() {
        var result = engine(weeks()).distribute(List.of(demand("1000.000001", "RAW_MATERIAL")));

        assertQuantity("333.333335", sum(result.details, "2026-10-26", "2026-10-30"));
        assertQuantity("333.333333", sum(result.details, "2026-11-02", "2026-11-06"));
        assertQuantity("333.333333", sum(result.details, "2026-11-09", "2026-11-13"));
        assertConserved("1000.000001", result);
        assertTrue(result.details.stream().allMatch(detail -> detail.getSystemQuantity().scale() <= 6));
        assertThrows(IllegalStateException.class, () -> engine(weeks())
                .distribute(List.of(demand("1000.000001", "MACHINE_OUTPUT"))));
    }

    @Test
    void missingPreviousMonthDoesNotFallBackToCurrentMonthOrDropFirstPortion() {
        var weeks = weeks().stream().filter(week -> week.getBusinessMonth().getMonthValue() == 11).toList();
        var result = engine(weeks).distribute(List.of(demand("500", "MACHINE_OUTPUT")));

        assertEquals(1, result.unallocated.size());
        assertEquals("BUSINESS_WEEK_MISSING", result.unallocated.getFirst().getReasonCode());
        assertQuantity("168", result.unallocated.getFirst().getQuantity());
        assertTrue(result.details.stream().allMatch(detail -> detail.getScheduleDate().getMonthValue() == 11));
        assertConserved("500", result);
    }

    @Test
    void overlappingWeeksKeepAffectedPortionsUnallocatedWithoutTrimmingDefinitions() {
        var weeks = weeks();
        weeks.get(1).setStartDate(LocalDate.of(2026, 10, 30));
        var result = engine(weeks).distribute(List.of(demand("500", "MACHINE_OUTPUT")));

        assertEquals(2, result.unallocated.size());
        assertTrue(result.unallocated.stream().allMatch(item -> "BUSINESS_WEEK_OVERLAP".equals(item.getReasonCode())));
        assertQuantity("166", result.details.stream().map(MrpDailyPlanDetail::getSystemQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(LocalDate.of(2026, 10, 30), weeks.get(1).getStartDate());
        assertConserved("500", result);
    }

    @Test
    void weekWithNoFactoryProductionDaysRetainsWholePortionInUnallocatedPool() {
        var calendar = calendar();
        calendar.stream().filter(day -> day.getCalendarDate().getMonthValue() == 10)
                .forEach(day -> day.setIsProductionDay(false));
        var result = new RollingBusinessWeekDistributionEngine(calendar, weeks(), 7L, 9L, "爱培科")
                .distribute(List.of(demand("500", "MACHINE_OUTPUT")));

        assertEquals("NO_PRODUCTION_DAY", result.unallocated.getFirst().getReasonCode());
        assertQuantity("168", result.unallocated.getFirst().getQuantity());
        assertConserved("500", result);
    }

    @Test
    void duplicateDemandKeysAggregateBeforeWritingUniqueFactsAndFactoryScopeIsEnforced() {
        var result = engine(weeks()).distribute(List.of(demand("200", "RAW_MATERIAL"), demand("300", "RAW_MATERIAL")));

        assertEquals(15, result.details.size());
        assertEquals(15, result.details.stream().map(MrpDailyPlanDetail::getRowKey).distinct().count());
        assertConserved("500", result);
        var foreign = demand("100", "MACHINE_OUTPUT");
        foreign.setFactoryCode("永惠");
        assertThrows(IllegalArgumentException.class, () -> engine(weeks()).distribute(List.of(foreign)));
    }

    @Test
    void decemberLastWeekWorksAcrossYearBoundary() {
        var weeks = List.of(week("2026-12-01", "W5", 5, "2026-12-28", "2027-01-01"),
                week("2027-01-01", "W1", 1, "2027-01-04", "2027-01-08"),
                week("2027-01-01", "W2", 2, "2027-01-11", "2027-01-15"));
        var calendar = new ArrayList<ProductionCalendarDay>();
        for (var week : weeks) for (LocalDate date = week.getStartDate(); !date.isAfter(week.getEndDate()); date = date.plusDays(1)) {
            var day = new ProductionCalendarDay(); day.setCalendarDate(date); day.setIsProductionDay(true); calendar.add(day);
        }
        var demand = demand("1001", "MACHINE_OUTPUT"); demand.setSourceMonth(LocalDate.of(2027, 1, 1));
        var result = new RollingBusinessWeekDistributionEngine(calendar, weeks, 7L, 9L, "爱培科").distribute(List.of(demand));

        assertEquals(LocalDate.of(2026, 12, 28), result.details.getFirst().getScheduleDate());
        assertQuantity("335", sum(result.details, "2026-12-28", "2027-01-01"));
        assertConserved("1001", result);
    }

    @Test
    void fourWeekMonthSplitsAllWeeksAndShiftsFirstPortionToSeptember() {
        var weeks = fullWeeks();
        var demand = demand("1003", "MACHINE_OUTPUT");
        demand.setSourceMonth(LocalDate.of(2026, 10, 1));
        var result = shifted(weeks, productionCalendar(weeks)).distribute(List.of(demand));
        assertTrue(result.unallocated.isEmpty());
        assertQuantity("253", sum(result.details, "2026-09-28", "2026-10-04"));
        for (String date : List.of("2026-10-05", "2026-10-12", "2026-10-19"))
            assertQuantity("250", sum(result.details, date, LocalDate.parse(date).plusDays(6).toString()));
        assertQuantity("0", sum(result.details, "2026-10-26", "2026-11-01"));
        assertTrue(result.details.stream().allMatch(d -> demand.getSourceMonth().equals(d.getSourceMonth())
                && d.getSourceDate().equals(d.getScheduleDate())));
        assertConserved("1003", result);
        // Weekly 253 is spread over production days with early dates receiving the daily remainder.
        assertEquals(List.of("51", "51", "51", "50", "50"), result.details.stream()
                .filter(d -> d.getScheduleDate().isBefore(LocalDate.of(2026, 10, 5)))
                .map(d -> d.getSystemQuantity().toPlainString()).toList());
    }

    @Test
    void fiveWeekMonthUsesFiveSharesAndLastWeekCarriesNextMonth() {
        var weeks = fullWeeks();
        var october = demand("1003", "MACHINE_OUTPUT"); october.setSourceMonth(LocalDate.of(2026, 10, 1));
        var november = demand("1003", "MACHINE_OUTPUT");
        var december = demand("20", "MACHINE_OUTPUT"); december.setSourceMonth(LocalDate.of(2026, 12, 1));
        var result = shifted(weeks, productionCalendar(weeks)).distribute(List.of(october, november, december));
        assertTrue(result.unallocated.isEmpty());
        assertQuantity("203", sum(result.details, "2026-10-26", "2026-11-01"));
        for (String date : List.of("2026-11-02", "2026-11-09", "2026-11-16", "2026-11-23"))
            assertQuantity("200", sum(result.details, date, LocalDate.parse(date).plusDays(6).toString()));
        assertQuantity("5", sum(result.details, "2026-11-30", "2026-12-06"));
        for (var demand : List.of(october, november, december))
            assertQuantity(demand.getQuantity().toPlainString(), result.details.stream()
                    .filter(d -> demand.getSourceMonth().equals(d.getSourceMonth()))
                    .map(MrpDailyPlanDetail::getSystemQuantity).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertConserved("2026", result);
    }

    @Test
    void shiftedFractionalDemandConservesSixDigitsAndSmallIntegersStayInFirstWeek() {
        var weeks = fullWeeks();
        var fractional = demand("1000.000001", "RAW_MATERIAL");
        var result = shifted(weeks, productionCalendar(weeks)).distribute(List.of(fractional));
        assertQuantity("200.000001", sum(result.details, "2026-10-26", "2026-11-01"));
        assertConserved("1000.000001", result);
        var small = shifted(weeks, productionCalendar(weeks)).distribute(List.of(demand("3", "MACHINE_OUTPUT")));
        assertQuantity("3", sum(small.details, "2026-10-26", "2026-11-01"));
        assertEquals(3, small.details.size());
        assertConserved("3", small);
    }

    @Test
    void shiftedMissingSourceMonthRetainsWholeQuantityAndMissingPrecedingMonthRetainsFirstShare() {
        var weeks = fullWeeks();
        var noCurrent = weeks.stream().filter(w -> w.getBusinessMonth().getMonthValue() != 11).toList();
        var result = shifted(noCurrent, productionCalendar(weeks)).distribute(List.of(demand("1003", "MACHINE_OUTPUT")));
        assertTrue(result.details.isEmpty());
        assertEquals("BUSINESS_WEEK_MISSING", result.unallocated.getFirst().getReasonCode());
        assertConserved("1003", result);
        var noPrevious = weeks.stream().filter(w -> w.getBusinessMonth().getMonthValue() != 10).toList();
        result = shifted(noPrevious, productionCalendar(weeks)).distribute(List.of(demand("1003", "MACHINE_OUTPUT")));
        assertQuantity("203", result.unallocated.getFirst().getQuantity());
        assertConserved("1003", result);
    }

    @Test
    void shiftedStoppedWeekAndIncompleteCalendarDoNotMoveQuantityToOtherWeeks() {
        var weeks = fullWeeks(); var calendar = productionCalendar(weeks);
        calendar.stream().filter(d -> !d.getCalendarDate().isBefore(LocalDate.of(2026, 11, 9))
                && !d.getCalendarDate().isAfter(LocalDate.of(2026, 11, 15))).forEach(d -> d.setIsProductionDay(false));
        calendar.removeIf(d -> d.getCalendarDate().equals(LocalDate.of(2026, 11, 16)));
        var result = shifted(weeks, calendar).distribute(List.of(demand("1003", "MACHINE_OUTPUT")));
        assertEquals(List.of("NO_PRODUCTION_DAY", "CALENDAR_INCOMPLETE"), result.unallocated.stream()
                .map(MrpUnallocatedQuantity::getReasonCode).toList());
        assertQuantity("400", result.unallocated.stream().map(MrpUnallocatedQuantity::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertConserved("1003", result);
    }

    @Test
    void shiftedAcrossYearBoundaryAndStableWeekOrdering() {
        var weeks = new ArrayList<BusinessWeekDetail>();
        weeks.add(week("2026-12-01", "W4", 4, "2026-12-28", "2027-01-03"));
        for (int i = 0; i < 4; i++) {
            var start = LocalDate.of(2027, 1, 4).plusWeeks(i);
            weeks.add(week("2027-01-01", "W" + (i + 1), i + 1, start.toString(), start.plusDays(6).toString()));
        }
        var calendar = productionCalendar(weeks);
        var demand = demand("1003", "MACHINE_OUTPUT"); demand.setSourceMonth(LocalDate.of(2027, 1, 1));
        var original = shifted(weeks, calendar).distribute(List.of(demand));
        java.util.Collections.reverse(weeks);
        var reordered = shifted(weeks, calendar).distribute(List.of(demand));
        assertEquals(original.details.stream().map(MrpDailyPlanDetail::getRowKey).toList(),
                reordered.details.stream().map(MrpDailyPlanDetail::getRowKey).toList());
        assertQuantity("253", sum(original.details, "2026-12-28", "2027-01-03"));
        assertConserved("1003", original);
    }

    @Test
    void leapFebruaryWithFourWeeksShiftsFirstShareIntoJanuaryAndKeepsLeapDayCoverage() {
        var weeks = new ArrayList<BusinessWeekDetail>();
        weeks.add(week("2028-01-01", "W5", 5, "2028-01-31", "2028-02-06"));
        weeks.add(week("2028-02-01", "W1", 1, "2028-02-07", "2028-02-13"));
        weeks.add(week("2028-02-01", "W2", 2, "2028-02-14", "2028-02-20"));
        weeks.add(week("2028-02-01", "W3", 3, "2028-02-21", "2028-02-27"));
        weeks.add(week("2028-02-01", "W4", 4, "2028-02-28", "2028-03-05"));
        var demand = demand("1003", "MACHINE_OUTPUT");
        demand.setSourceMonth(LocalDate.of(2028, 2, 1));
        var result = shifted(weeks, productionCalendar(weeks)).distribute(List.of(demand));
        assertTrue(result.unallocated.isEmpty());
        assertQuantity("253", sum(result.details, "2028-01-31", "2028-02-06"));
        assertQuantity("250", sum(result.details, "2028-02-07", "2028-02-13"));
        assertQuantity("250", sum(result.details, "2028-02-14", "2028-02-20"));
        assertQuantity("250", sum(result.details, "2028-02-21", "2028-02-27"));
        assertQuantity("0", sum(result.details, "2028-02-28", "2028-03-05"));
        assertConserved("1003", result);
    }

    @Test
    void shiftedInvalidLastSourceWeekBlocksWholeSplitEvenThoughItIsNotATarget() {
        var weeks = fullWeeks();
        weeks.stream().filter(w -> w.getBusinessMonth().getMonthValue() == 11 && w.getSortNo() == 5)
                .forEach(w -> w.setEndDate(w.getStartDate().minusDays(1)));
        var result = shifted(weeks, productionCalendar(fullWeeks())).distribute(List.of(demand("1003", "MACHINE_OUTPUT")));
        assertTrue(result.details.isEmpty());
        assertEquals("BUSINESS_WEEK_INVALID", result.unallocated.getFirst().getReasonCode());
        assertConserved("1003", result);
    }

    private List<BusinessWeekDetail> fullWeeks() {
        var weeks = new ArrayList<BusinessWeekDetail>();
        weeks.add(week("2026-09-01", "W5", 5, "2026-09-28", "2026-10-04"));
        for (int m = 10; m <= 12; m++) {
            int count = m == 11 ? 5 : 4;
            var first = m == 10 ? LocalDate.of(2026, 10, 5) : m == 11 ? LocalDate.of(2026, 11, 2) : LocalDate.of(2026, 12, 7);
            for (int i = 0; i < count; i++) {
                var start = first.plusWeeks(i);
                weeks.add(week("2026-" + m + "-01", "W" + (i + 1), i + 1, start.toString(), start.plusDays(6).toString()));
            }
        }
        return weeks;
    }

    private List<ProductionCalendarDay> productionCalendar(List<BusinessWeekDetail> weeks) {
        var days = new ArrayList<ProductionCalendarDay>();
        for (var week : weeks) for (var date = week.getStartDate(); !date.isAfter(week.getEndDate()); date = date.plusDays(1)) {
            var day = new ProductionCalendarDay(); day.setFactoryCode("爱培科"); day.setCalendarDate(date);
            day.setIsProductionDay(date.getDayOfWeek().getValue() <= 5); day.setDailyCapacity(BigDecimal.ONE);
            days.add(day);
        }
        return days;
    }

    private RollingBusinessWeekDistributionEngine shifted(List<BusinessWeekDetail> weeks, List<ProductionCalendarDay> days) {
        return new RollingBusinessWeekDistributionEngine(days, weeks, 7L, 9L, "爱培科", true);
    }

    private RollingBusinessWeekDistributionEngine engine(List<BusinessWeekDetail> weeks) {
        return new RollingBusinessWeekDistributionEngine(calendar(), weeks, 7L, 9L, "爱培科");
    }

    private List<BusinessWeekDetail> weeks() {
        return new ArrayList<>(List.of(week("2026-10-01", "W5", 5, "2026-10-26", "2026-10-30"),
                week("2026-11-01", "W1", 1, "2026-11-02", "2026-11-06"),
                week("2026-11-01", "W2", 2, "2026-11-09", "2026-11-13")));
    }

    private BusinessWeekDetail week(String month, String code, int sort, String start, String end) {
        var week = new BusinessWeekDetail(); week.setBusinessMonth(LocalDate.parse(month)); week.setWeekCode(code);
        week.setSortNo(sort); week.setStartDate(LocalDate.parse(start)); week.setEndDate(LocalDate.parse(end)); return week;
    }

    private List<ProductionCalendarDay> calendar() {
        var calendar = new ArrayList<ProductionCalendarDay>();
        for (var week : weeks()) for (LocalDate date = week.getStartDate(); !date.isAfter(week.getEndDate()); date = date.plusDays(1)) {
            var day = new ProductionCalendarDay(); day.setFactoryCode("爱培科"); day.setCalendarDate(date);
            day.setIsProductionDay(true); day.setAllocationWeight(BigDecimal.ONE); day.setDailyCapacity(BigDecimal.ONE);
            calendar.add(day);
        }
        return calendar;
    }

    private MrpTerminalDemand demand(String quantity, String type) {
        var demand = new MrpTerminalDemand(); demand.setFactoryCode("爱培科"); demand.setRootMaterialCode("A");
        demand.setTerminalMaterialCode("MACHINE_OUTPUT".equals(type) ? "A" : "D"); demand.setTerminalType(type);
        demand.setSupplyMode("SELF_MADE"); demand.setSourceMonth(LocalDate.of(2026, 11, 1));
        demand.setQuantity(new BigDecimal(quantity)); return demand;
    }

    private BigDecimal sum(List<MrpDailyPlanDetail> plans, String start, String end) {
        return plans.stream().filter(plan -> !plan.getScheduleDate().isBefore(LocalDate.parse(start))
                        && !plan.getScheduleDate().isAfter(LocalDate.parse(end)))
                .map(MrpDailyPlanDetail::getSystemQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void assertConserved(String expected, DailyDistributionEngine.DistributionResult result) {
        assertQuantity(expected, result.details.stream().map(MrpDailyPlanDetail::getSystemQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add).add(result.unallocated.stream()
                        .map(MrpUnallocatedQuantity::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    private void assertQuantity(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), expected + " != " + actual.toPlainString());
    }
}
