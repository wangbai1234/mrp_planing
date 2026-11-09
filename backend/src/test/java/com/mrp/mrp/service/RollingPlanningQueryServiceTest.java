package com.mrp.mrp.service;

import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.mrp.repository.MrpDailyExecutionMapper;
import com.mrp.mrp.repository.RollingPlanningMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RollingPlanningQueryServiceTest {
    @Mock RollingPlanningMapper mapper;
    @Mock MrpCalcBatchMapper batches;
    @Mock DailyPlanningService daily;
    @Mock DailyCapacityService capacity;
    @Mock MrpDailyExecutionMapper executions;
    @Mock PlanningFactoryAccessService access;
    private static final LocalDate START=LocalDate.of(2026,9,1), END=LocalDate.of(2027,3,31);

    @Test void broadDeepPageKeepsBoundedPayloadQueryWhenVirtualDatesExceedPageSize() {
        batch();
        when(mapper.hasOverrides(11L)).thenReturn(true);
        when(mapper.countDetails(12L,11L,null,null,null,null,null,START,END,true,true)).thenReturn(100000L);
        when(mapper.countVirtualDetails(12L,11L,null,null,null,null,null,START,END)).thenReturn(2000L);
        var result=service().details(11L,986,100,null,null,null,null,START,END);
        assertEquals(100000L,result.total());
        // Passing V selects the narrow-key window; null would sort every wide DTO.
        verify(mapper).selectDetails(12L,11L,null,null,null,null,null,START,END,true,true,98500L,100,2000L);
        verify(access).require(any(MrpCalcBatch.class));
    }

    @Test void exactFactoryLookupRetainsItsSelectiveQuery() {
        batch();when(mapper.hasOverrides(11L)).thenReturn(true);
        service().details(11L,1,20,"F1",null,null,null,START,END);
        verify(mapper,never()).countVirtualDetails(anyLong(),anyLong(),any(),any(),any(),any(),any(),any(),any());
        verify(mapper).selectDetails(12L,11L,"F1",null,null,null,null,START,END,true,true,0L,20,null);
    }

    @Test void automaticOnlyPageDoesNotCountOrMergeVirtualDates() {
        batch();
        service().details(11L,2,20,null,null,null,null,START,END);
        verify(mapper,never()).countVirtualDetails(anyLong(),anyLong(),any(),any(),any(),any(),any(),any(),any());
        verify(mapper).selectDetails(12L,11L,null,null,null,null,null,START,END,true,false,20L,20,null);
    }
    @Test void machineGridFiltersTheExactSourceRoot() {
        batch();
        when(mapper.selectMonths(any())).thenReturn(List.of(START));
        service().grid(11L,1,20,null,null,"ROOT","MACHINE_OUTPUT",false);
        verify(mapper).countRows(12L,null,null,"ROOT","MACHINE_OUTPUT");
        verify(mapper).selectRowPage(12L,null,null,"ROOT","MACHINE_OUTPUT",0L,20);
    }

    private void batch() {
        var batch=new MrpCalcBatch();batch.setId(11L);batch.setActiveRunId(12L);
        batch.setRuleVersion("v4-rolling-business-weeks");when(batches.selectById(11L)).thenReturn(batch);
    }
    private RollingPlanningQueryService service() {
        return new RollingPlanningQueryService(mapper,batches,daily,capacity,executions,access);
    }
}
