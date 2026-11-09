package com.mrp.mrp.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.audit.service.AuditService;
import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.repository.MaterialMapper;
import com.mrp.mrp.domain.*;
import com.mrp.mrp.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MaterialPolicyNoticeServiceTest {
    private final MrpMaterialPolicyNoticeMapper mapper = mock(MrpMaterialPolicyNoticeMapper.class);
    private final MaterialMapper materials = mock(MaterialMapper.class);
    private final MrpCalcBatchMapper batches = mock(MrpCalcBatchMapper.class);
    private final ObjectMapper json = new ObjectMapper();
    private final AuditService audit = mock(AuditService.class);
    private final MaterialPolicyNoticeService service = new MaterialPolicyNoticeService(mapper, materials, batches, json, audit);
    @Test void deduplicatesMaterialAndPreservesAllDistinctSourcesAndPartialStop() throws Exception {
        MrpCalcBatch batch = new MrpCalcBatch(); batch.setId(1L);
        when(materials.selectByCodes(anyList())).thenReturn(List.of(new Material(1L, "B", "组件B", null, null, null, null, null, null, null, null, true, null, null, null, null, false, null, null, null)));
        MaterialPolicyDetail partial = new MaterialPolicyDetail(); partial.setExplosionPolicy("STOP");
        var result = service.evaluate(batch, 10L, List.of(detail("A", "A→B"), detail("A", "A→B"), detail("D", "D→B")), Map.of("B", partial));
        assertEquals(1, result.size()); var notice = result.getFirst();
        assertEquals("SUPPLY", notice.getMissingType()); assertEquals("STOP", notice.getEffectiveExplosionPolicy());
        assertEquals("组件B", notice.getMaterialName()); assertEquals(2, json.readTree(notice.getSourcesJson()).size());
        assertEquals(10L, notice.getRunId());
        verify(materials).selectByCodes(List.of("B"));
    }
    @Test void completePolicyProducesNoNoticeAndNoMaterialQuery() {
        MaterialPolicyDetail policy = new MaterialPolicyDetail(); policy.setSupplyMode("SELF_MADE"); policy.setExplosionPolicy("EXPLODE");
        assertTrue(service.evaluate(new MrpCalcBatch(), 10L, List.of(detail("A", "A→B")), Map.of("B", policy)).isEmpty());
        verifyNoInteractions(materials);
    }
    @Test void emptyRecordClearsPreviousCandidateAndDoesNotInsertEmptyBatch() {
        service.record(new MrpCalcBatch(), 10L, List.of(), Map.of()); verify(mapper).deleteByRunId(10L); verify(mapper, never()).insertBatch(anyList());
    }
    @Test void pageOnlyReadsActiveRunAndRequiresFactoryScope() {
        MrpCalcBatch batch = new MrpCalcBatch(); batch.setId(1L); batch.setFactoryCode("F1"); batch.setActiveRunId(10L);
        when(batches.selectById(1L)).thenReturn(batch); when(mapper.canReadFactory(null, "F1")).thenReturn(1);
        when(mapper.selectPage(10L, "B", "SUPPLY", 0, 100)).thenReturn(List.of());
        service.page(1L, "B", "SUPPLY", 0, 1000); verify(mapper).countPage(10L, "B", "SUPPLY");
        when(mapper.canReadFactory(null, "F1")).thenReturn(0);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.page(1L, "", "", 1, 20));
    }
    @Test void pendingCountUsesOnlyActiveRunAndLeavesHistoricalNoticesUntouched() {
        MrpCalcBatch batch = new MrpCalcBatch(); batch.setId(1L); batch.setFactoryCode("F1"); batch.setActiveRunId(10L);
        when(batches.selectById(1L)).thenReturn(batch); when(mapper.canReadFactory(null, "F1")).thenReturn(1);
        when(mapper.countPendingDefaultExplosion(10L)).thenReturn(2);
        assertEquals(2, service.pendingDefaultExplosionCount(1L));
        verify(mapper).countPendingDefaultExplosion(10L);
        verify(mapper, never()).confirmDefaultExplosion(anyLong(), any());
    }
    @Test void confirmationRequiresAuthenticatedOperatorAndAnActiveRun() {
        MrpCalcBatch batch = new MrpCalcBatch(); batch.setId(1L); batch.setFactoryCode("F1");
        when(batches.selectById(1L)).thenReturn(batch); when(mapper.canReadFactory(null, "F1")).thenReturn(1);
        assertThrows(IllegalStateException.class, () -> service.confirmDefaultExplosion(1L));
        batch.setActiveRunId(10L);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.confirmDefaultExplosion(1L));
        verify(mapper, never()).confirmDefaultExplosion(anyLong(), anyLong());
        verifyNoInteractions(audit);
    }
    private MrpRequirementDetail detail(String root, String path) {
        MrpRequirementDetail d = new MrpRequirementDetail(); d.setFactoryCode("F1"); d.setCurrentMaterialCode("B"); d.setRootMaterialCode(root); d.setSourceMonth(LocalDate.of(2027, 2, 1)); d.setPathText(path); return d;
    }
}
