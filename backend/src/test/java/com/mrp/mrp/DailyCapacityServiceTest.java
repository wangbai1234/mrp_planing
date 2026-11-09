package com.mrp.mrp;

import com.mrp.mrp.domain.*;
import com.mrp.mrp.repository.DailyCapacityMapper;
import com.mrp.mrp.repository.ProductionCalendarDayMapper;
import com.mrp.mrp.service.DailyCapacityService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DailyCapacityServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 14);
    private static final String FACTORY = "爱培科";

    @Test void aggregatesAllSelfMadeMaterialsForOneFactoryDay() {
        var result = evaluate(List.of(plan("A", "SELF_MADE", "600"), plan("B", "SELF_MADE", "600")), day(true, "1000", "件"), Map.of("A", "件", "B", "件"));
        assertEquals("OVERLOAD", result.status());
        decimal("1200", result.load()); decimal("200", result.excess());
    }

    @Test void exactCapacityIsNormal() {
        var result = evaluate(List.of(plan("A", "SELF_MADE", "1000.000000")), day(true, "1000", "件"), Map.of("A", "件"));
        assertEquals("NORMAL", result.status()); assertEquals("满产", result.message()); decimal("0", result.excess());
    }

    @Test void purchasedOutsourcedAndOtherFactoriesDoNotConsumeFactoryCapacity() {
        var other = plan("D", "SELF_MADE", "5000"); other.setFactoryCode("永惠");
        var result = evaluate(List.of(plan("A", "SELF_MADE", "100"), plan("B", "PURCHASED", "5000"), plan("C", "OUTSOURCED", "5000"), other), day(true, "1000", "件"), Map.of("A", "件"));
        assertEquals("NORMAL", result.status()); decimal("100", result.load()); assertFalse(result.partiallyUnassessed());
    }

    @Test void usesEffectiveQuantityOnFinalScheduleDate() {
        var fact = plan("A", "SELF_MADE", "1200"); fact.setEffectiveQuantity(new BigDecimal("900")); fact.setSourceDate(DATE.plusDays(7));
        var result = evaluate(List.of(fact), day(true, "1000", "件"), Map.of("A", "件"));
        assertEquals("NORMAL", result.status()); decimal("900", result.load()); decimal("1200", fact.getSystemQuantity());
    }

    @Test void unknownSupplyAndIncompatibleUnitsCannotBeDeclaredNormal() {
        for (var fact : List.of(plan("A", "UNMAINTAINED", "600"), plan("A", "SELF_MADE", "600"))) {
            var result = evaluate(List.of(fact), day(true, "1000", "件"), Map.of("A", "kg"));
            assertEquals("UNASSESSED", result.status()); assertTrue(result.partiallyUnassessed()); decimal("0", result.load());
        }
    }

    @Test void missingMaterialUnitAndMissingCalendarAreUnassessed() {
        var fact = plan("A", "SELF_MADE", "600");
        assertEquals("UNASSESSED", evaluate(List.of(fact), day(true, "1000", "件"), Map.of()).status());
        var result = evaluate(List.of(fact), null, Map.of("A", "件"));
        assertEquals("UNASSESSED", result.status()); assertFalse(result.configured()); assertNull(result.dailyCapacity());
    }

    @Test void knownOverloadRemainsVisibleWithUnassessedDemand() {
        var result = evaluate(List.of(plan("A", "SELF_MADE", "1200"), plan("B", "SELF_MADE", "600")), day(true, "1000", "件"), Map.of("A", "件", "B", "kg"));
        assertEquals("OVERLOAD", result.status()); assertTrue(result.partiallyUnassessed()); decimal("1200", result.load());
    }

    @Test void selfMadeLoadOnRestDayWarnsEvenWhenItsUnitIsUnknown() {
        var result = evaluate(List.of(plan("A", "SELF_MADE", "600")), day(false, "0", "件"), Map.of());
        assertEquals("REST_LOAD", result.status()); assertFalse(result.productionDay());
    }

    @Test void zeroDemandDoesNotCreateFalseUnitWarnings() {
        var result = evaluate(List.of(plan("A", "UNMAINTAINED", "0")), day(true, "1000", "件"), Map.of());
        assertEquals("NORMAL", result.status()); assertFalse(result.partiallyUnassessed());
    }

    @Test void restDayWithoutUnitAndWithoutOwnLoadIsNormal() {
        var result = evaluate(List.of(), day(false, "0", null), Map.of());
        assertEquals("NORMAL", result.status()); assertEquals("停产", result.message());
    }

    @Test void productionDayWithBlankCapacityUnitIsUnassessedEvenWithoutLoad() {
        var result = evaluate(List.of(), day(true, "1000", "   "), Map.of());
        assertEquals("UNASSESSED", result.status());
    }

    @Test void evaluationReadsOnlyBatchFrozenCalendarAndUnits() {
        var units = mock(DailyCapacityMapper.class); var calendar = mock(ProductionCalendarDayMapper.class);
        var batch = new MrpCalcBatch(); batch.setFactoryCode(FACTORY); batch.setProductionCalendarVersionId(42L);
        var foreign = day(true, "1", "kg"); foreign.setFactoryCode("永惠");
        when(calendar.selectByVersionId(42L)).thenReturn(List.of(day(true, "1000", "件"), foreign));
        when(units.selectUnits(42L)).thenReturn(List.of(new DailyCapacityMapper.MaterialUnit("A", "件"), new DailyCapacityMapper.MaterialUnit("B", null)));
        var result = new DailyCapacityService(units, calendar, mock(com.mrp.mrp.repository.MrpCalcBatchMapper.class))
                .evaluate(batch, List.of(plan("A", "SELF_MADE", "900")), DATE, DATE).get(0);
        assertEquals("NORMAL", result.status()); decimal("1000", result.dailyCapacity());
        verify(calendar).selectByVersionId(42L); verify(units).selectUnits(42L); verifyNoMoreInteractions(calendar, units);
    }

    @Test void wholeMachineOutputIgnoresBomLeafQuantitySupplyAndMaterialUnit() {
        var output = plan("ROOT", "PURCHASED", "1200"); output.setTerminalType("MACHINE_OUTPUT"); output.setEffectiveQuantity(new BigDecimal("900"));
        var leaf = plan("PCBA", "SELF_MADE", "5000"); leaf.setTerminalType("PCBA");
        var result = DailyCapacityService.evaluateMachineOutput(FACTORY,List.of(output,leaf),Map.of(DATE,day(true,"1000","台")),DATE,DATE).getFirst();
        assertEquals("NORMAL",result.status()); decimal("900",result.load()); assertEquals("MACHINE_OUTPUT",result.capacityBasis());
    }

    @Test void legacyNonMachineUnitsAreUnassessedInWholeMachineVersion() {
        var output = plan("ROOT", "SELF_MADE", "1200"); output.setTerminalType("MACHINE_OUTPUT");
        for (String unit : List.of("件","kg")) {
            var result = DailyCapacityService.evaluateMachineOutput(FACTORY,List.of(output),Map.of(DATE,day(true,"1000",unit)),DATE,DATE).getFirst();
            assertEquals("UNASSESSED",result.status()); decimal("1200",result.load()); assertNull(result.excess());
        }
    }

    @Test void wholeMachineOverloadCountsOtherRootsAndRestLoadWarns() {
        var a = plan("ROOT_A", "SELF_MADE", "600"); a.setTerminalType("MACHINE_OUTPUT");
        var b = plan("ROOT_B", "SELF_MADE", "600"); b.setTerminalType("MACHINE_OUTPUT");
        var overload = DailyCapacityService.evaluateMachineOutput(FACTORY,List.of(a,b),Map.of(DATE,day(true,"1000","台")),DATE,DATE).getFirst();
        assertEquals("OVERLOAD",overload.status()); decimal("200",overload.excess());
        assertEquals("REST_LOAD",DailyCapacityService.evaluateMachineOutput(FACTORY,List.of(a),Map.of(DATE,day(false,"0",null)),DATE,DATE).getFirst().status());
    }

    @Test void historicalTerminalCapacityNeverCountsMachineOutput() {
        var output = plan("ROOT", "SELF_MADE", "1200"); output.setTerminalType("MACHINE_OUTPUT");
        var leaf = plan("PCBA", "SELF_MADE", "600"); leaf.setTerminalType("PCBA");
        var result = evaluate(List.of(output,leaf),day(true,"1000","件"),Map.of("ROOT","件","PCBA","件"));
        decimal("600",result.load()); assertEquals("TERMINAL",result.capacityBasis());
    }

    private static DailyCapacityService.DayLoad evaluate(List<MrpDailyPlanDetail> facts, ProductionCalendarDay day, Map<String,String> units) {
        return DailyCapacityService.evaluate(FACTORY, facts, day == null ? Map.of() : Map.of(DATE, day), units, DATE, DATE).get(0);
    }
    private static ProductionCalendarDay day(boolean production, String quantity, String unit) {
        var day = new ProductionCalendarDay(); day.setFactoryCode(FACTORY); day.setCalendarDate(DATE);
        day.setIsProductionDay(production); day.setDailyCapacity(new BigDecimal(quantity)); day.setCapacityUnit(unit); return day;
    }
    private static MrpDailyPlanDetail plan(String material, String supply, String quantity) {
        var fact = new MrpDailyPlanDetail(); fact.setFactoryCode(FACTORY); fact.setTerminalMaterialCode(material);
        fact.setSupplyMode(supply); fact.setScheduleDate(DATE); fact.setSystemQuantity(new BigDecimal(quantity)); return fact;
    }
    private static void decimal(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
}
