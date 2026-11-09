package com.mrp.mrp.service;

import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.repository.ForecastMapper;
import com.mrp.mrp.domain.*;
import com.mrp.mrp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanningCalendarValidationServiceTest {
    ForecastMapper forecasts = mock(ForecastMapper.class);
    MrpCalcBatchMapper batches = mock(MrpCalcBatchMapper.class);
    ConfigMasterMapper master = mock(ConfigMasterMapper.class);
    ProductionCalendarDayMapper calendars = mock(ProductionCalendarDayMapper.class);
    PlanningCalendarValidationService service = new PlanningCalendarValidationService(forecasts,batches,master,calendars);
    MrpCalcBatch batch = new MrpCalcBatch();
    LocalDate month = LocalDate.parse("2026-10-01");
    List<ProductionCalendarDay> frozen = new ArrayList<>();
    List<BusinessWeekDetail> weeks = new ArrayList<>();
    @BeforeEach void fixture() {
        batch.setId(10L); batch.setForecastVersionId(2L); batch.setFactoryCode("永惠");
        batch.setRuleVersion(ProductionCapacityRules.VERSION); batch.setProductionCalendarVersionId(3L);
        when(forecasts.selectDetailsByVersionId(2L)).thenReturn(List.of(forecast("永惠", BigDecimal.TEN),forecast("爱培科",BigDecimal.TEN)));
        weeks.add(week(month.minusMonths(1),"W4","2026-09-21","2026-09-30",4));
        weeks.add(week(month,"W1","2026-10-01","2026-10-07",1));
        weeks.add(week(month,"W2","2026-10-08","2026-10-14",2));
        weeks.add(week(month,"W3","2026-10-15","2026-10-21",3));
        weeks.add(week(month,"W4","2026-10-22","2026-10-31",4));
        when(batches.selectBusinessWeeksByBatchAndRange(any(),any(),any())).thenReturn(weeks);
        when(batches.selectBusinessWeekMonthsByBatchId(10L)).thenReturn(List.of(month.minusMonths(1), month));
        LocalDate.parse("2026-09-21").datesUntil(LocalDate.parse("2026-10-22")).forEach(date -> {
            var day = new ProductionCalendarDay();day.setFactoryCode("永惠");day.setCalendarDate(date);day.setIsProductionDay(false);frozen.add(day);
        });
        when(calendars.selectByVersionId(3L)).thenReturn(frozen);
    }
    @Test void configuredRestDaysPassAndUnrelatedFactoryAndUnusedFinalWeekAreExcluded() {
        assertEquals(0,service.check(batch,false).total());
        verify(master,never()).selectProductionCalendar(any());
    }
    @Test void missingDateAndPriorMonthBoundaryAreDetectedEvenWithoutDailyFacts() {
        frozen.removeIf(day -> List.of("2026-10-01","2026-09-21").contains(day.getCalendarDate().toString()));
        var result = service.check(batch,false);
        assertEquals(2,result.total());
        assertEquals(LocalDate.parse("2026-09-21"),result.missingDays().getFirst().date());
        assertEquals(month,result.missingDays().getLast().date());
    }
    @Test void revisionChecksLatestButPublicationRetainsFrozenCalendar() {
        var maintained = frozen.stream().filter(d -> !month.equals(d.getCalendarDate())).map(d -> {
            var row = new ProductionCalendarEntry();row.setFactoryCode(d.getFactoryCode());row.setCalendarDate(d.getCalendarDate());row.setProductionDay(false);return row;
        }).toList();
        when(master.selectProductionCalendar("永惠")).thenReturn(maintained);
        assertEquals(0,service.check(batch,false).total());
        assertEquals(List.of(new PlanningCalendarValidationService.MissingDay("永惠",month)),service.check(batch,true).missingDays());
    }
    @Test void v4ChecksOnlyPreviousLastWeekAndCurrentFirstTwoWeeks() {
        batch.setRuleVersion(ProductionCapacityRules.ROLLING_V4);
        frozen.removeIf(day -> !day.getCalendarDate().isBefore(LocalDate.parse("2026-10-15")));
        assertEquals(0,service.check(batch,false).total());
    }
    @Test void zeroForecastDoesNotRequireCalendar() {
        when(forecasts.selectDetailsByVersionId(2L)).thenReturn(List.of(forecast("永惠",BigDecimal.ZERO)));
        assertEquals(0,service.check(batch,false).total());
    }
    @Test void missingWeekDefinitionCannotSilentlyPass() {
        weeks.clear();
        assertEquals(2,service.check(batch,false).total());
    }
    ForecastDetail forecast(String factory, BigDecimal quantity) {
        return new ForecastDetail(1L,2L,factory,"ROOT",null,null,null,null,null,null,null,month,quantity);
    }
    BusinessWeekDetail week(LocalDate m,String code,String start,String end,int sort) {
        var w = new BusinessWeekDetail(); w.setBusinessMonth(m);w.setWeekCode(code);w.setStartDate(LocalDate.parse(start));w.setEndDate(LocalDate.parse(end));w.setSortNo(sort);return w;
    }
}
