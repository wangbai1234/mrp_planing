package com.mrp.mrp.service;

import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanningReleaseValidationServiceTest {
    @Test void allPendingBadgesBlockButConfirmedDefaultsAndResolvedRisksPass() {
        var issues = mock(MrpDecisionIssueMapper.class); var quantities = mock(MrpUnallocatedQuantityMapper.class);
        var policies = mock(MrpMaterialPolicyNoticeMapper.class); var risks = mock(MrpStockRiskMapper.class);
        var calendars = mock(PlanningCalendarValidationService.class);
        var service = new PlanningReleaseValidationService(issues, quantities, policies, risks, calendars);
        var b = new MrpCalcBatch(); b.setId(10L); b.setActiveRunId(20L);
        when(issues.countByBatchId(10L,"OPEN",null)).thenReturn(1,0);
        when(quantities.countByRunIdAndStatus(20L,"PENDING",null)).thenReturn(2,0);
        when(policies.countPendingDefaultExplosion(20L)).thenReturn(24,0);
        when(risks.countByBatchId(10L,20L)).thenReturn(25,0);
        when(calendars.check(b,false)).thenReturn(new PlanningCalendarValidationService.CalendarCheck(1,
                List.of(new PlanningCalendarValidationService.MissingDay("永惠", LocalDate.parse("2026-10-01"))),List.of()),
                new PlanningCalendarValidationService.CalendarCheck(0,List.of(),List.of()));
        String error = assertThrows(IllegalStateException.class, () -> service.requirePublishable(b)).getMessage();
        for (String text : List.of("异常 1", "需求 2", "下钻待确认 24", "库存风险待处理 25", "永惠 2026-10-01")) assertTrue(error.contains(text), error);
        assertDoesNotThrow(() -> service.requirePublishable(b));
    }
}
