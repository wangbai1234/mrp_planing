package com.mrp.mrp.controller;

import com.mrp.mrp.domain.*;
import com.mrp.mrp.repository.*;
import com.mrp.mrp.service.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WholeMachineGridTest {
    private final LocalDate date = LocalDate.of(2026,10,14);
    @Test void defaultMachineGridFiltersRowsWithoutReducingFactoryCapacityLoad() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test", null, List.of(new SimpleGrantedAuthority("mrp_shortage:view"),
                        new SimpleGrantedAuthority("mrp_workspace:view"))));
        try {
        var planning = mock(DailyPlanningService.class);
        var details = mock(MrpDailyPlanDetailMapper.class);
        var batches = mock(MrpCalcBatchMapper.class);
        var calendar = mock(ProductionCalendarDayMapper.class);
        var capacity = new DailyCapacityService(mock(DailyCapacityMapper.class),calendar,batches);
        var batch = new MrpCalcBatch(); batch.setId(1L); batch.setActiveRunId(2L); batch.setFactoryCode("爱培科");
        batch.setRuleVersion(ProductionCapacityRules.VERSION); batch.setProductionCalendarVersionId(3L);
        when(batches.selectById(1L)).thenReturn(batch);
        var day = new ProductionCalendarDay(); day.setFactoryCode("爱培科"); day.setCalendarDate(date);
        day.setIsProductionDay(true); day.setDailyCapacity(new BigDecimal("1000")); day.setCapacityUnit("台");
        when(calendar.selectByVersionId(3L)).thenReturn(List.of(day));
        when(calendar.selectByFactoryAndDateRange(3L,"爱培科",date,date)).thenReturn(List.of(day));
        var plans = List.of(plan("ROOT_A","MACHINE_OUTPUT","600"),plan("ROOT_B","MACHINE_OUTPUT","600"),plan("ROOT_A","PCBA","5000"));
        when(details.selectByRunId(2L)).thenReturn(plans);
        when(planning.applyOverrides(1L,plans)).thenReturn(plans);
        var controller = new MrpDailyPlanningController(planning,details,batches,mock(MrpCalcRunMapper.class),calendar,capacity,mock(com.mrp.mrp.repository.RollingPlanningMapper.class));
        var response = controller.getDailyGrid(1L,1,1,null,"ROOT_A",null,null,null,null,date,date).getBody().data();
        assertEquals("MACHINE_OUTPUT",response.view); assertEquals("MACHINE_OUTPUT",response.capacityBasis);
        assertEquals(ProductionCapacityRules.VERSION,response.ruleVersion);
        assertEquals(1,response.totalRows); assertEquals("台",response.rows.getFirst().materialUnit);
        assertEquals("OVERLOAD",response.capacityLoads.getFirst().status());
        assertEquals(0,new BigDecimal("1200").compareTo(response.capacityLoads.getFirst().load()));
        var terminal = controller.getDailyGrid(1L,1,20,null,null,null,null,null,"TERMINAL",date,date).getBody().data();
        assertEquals(1,terminal.totalRows); assertEquals("PCBA",terminal.rows.getFirst().terminalType);
        assertEquals("MACHINE_OUTPUT",terminal.capacityBasis);
        assertEquals("OVERLOAD",terminal.capacityLoads.getFirst().status());
        batch.setRuleVersion("v2-daily-capacity");
        var historical = controller.getDailyGrid(1L,1,20,null,null,null,null,null,null,date,date).getBody().data();
        assertEquals("TERMINAL",historical.view); assertEquals("TERMINAL",historical.capacityBasis);
        assertEquals(1,historical.totalRows);
        } finally { SecurityContextHolder.clearContext(); }
    }
    private MrpDailyPlanDetail plan(String root,String type,String quantity) {
        var plan = new MrpDailyPlanDetail(); plan.setFactoryCode("爱培科"); plan.setRootMaterialCode(root);
        plan.setTerminalMaterialCode("MACHINE_OUTPUT".equals(type) ? root : "LEAF"); plan.setTerminalType(type);
        plan.setSupplyMode("SELF_MADE"); plan.setPathGroupKey(root+"|"+type); plan.setScheduleDate(date);
        plan.setSystemQuantity(new BigDecimal(quantity)); return plan;
    }
}
