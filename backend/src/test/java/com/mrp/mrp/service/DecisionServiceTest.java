package com.mrp.mrp.service;

import com.mrp.mrp.domain.*;
import com.mrp.mrp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionServiceTest {

    @Mock
    private MrpDecisionIssueMapper issueMapper;
    @Mock
    private MrpDecisionRecordMapper recordMapper;
    @Mock
    private MrpCalcBatchMapper batchMapper;

    @InjectMocks
    private DecisionService decisionService;

    private MrpDecisionIssue testIssue;
    private MrpCalcBatch testBatch;

    @BeforeEach
    void setUp() {
        testIssue = new MrpDecisionIssue();
        testIssue.setId(1L);
        testIssue.setBatchId(1L);
        testIssue.setIssueKey("TEST-001");
        testIssue.setIssueType("INVENTORY_MISSING");
        testIssue.setSeverity("BLOCKING");
        testIssue.setStatus("OPEN");
        testIssue.setRevision(0);
        testIssue.setCreatedAt(LocalDateTime.now());

        testBatch = new MrpCalcBatch();
        testBatch.setId(1L);
        testBatch.setStatus("WAITING_DECISION");
        testBatch.setUnresolvedCount(1);
    }

    @Test
    void testRecordDecision() {
        // Given
        when(issueMapper.selectById(1L)).thenReturn(testIssue);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(Collections.emptyList());

        // When
        decisionService.recordDecision(1L, "APPROVED", null, "test decision", 1L, 0);

        // Then
        verify(recordMapper).insert(any());
        verify(issueMapper).update(argThat(i -> "RESOLVED".equals(i.getStatus())));
        verify(batchMapper).update(argThat(b -> 
            "READY_TO_CALCULATE".equals(b.getStatus()) && b.getUnresolvedCount() == 0));
    }

    @Test
    void testRecordDecisionWithRemainingIssues() {
        // Given
        when(issueMapper.selectById(1L)).thenReturn(testIssue);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        
        MrpDecisionIssue anotherIssue = new MrpDecisionIssue();
        anotherIssue.setId(2L);
        anotherIssue.setStatus("OPEN");
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(List.of(anotherIssue));

        // When
        decisionService.recordDecision(1L, "APPROVED", null, "test decision", 1L, 0);

        // Then
        verify(batchMapper).update(argThat(b -> 
            "WAITING_DECISION".equals(b.getStatus()) && b.getUnresolvedCount() == 1));
    }

    @Test
    void testGetIssuesByBatch() {
        // Given
        when(issueMapper.selectByBatchId(1L)).thenReturn(List.of(testIssue));

        // When
        List<MrpDecisionIssue> result = decisionService.getIssuesByBatch(1L);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("TEST-001", result.get(0).getIssueKey());
    }

    @Test
    void testGetOpenIssuesByBatch() {
        // Given
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(List.of(testIssue));

        // When
        List<MrpDecisionIssue> result = decisionService.getOpenIssuesByBatch(1L);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("OPEN", result.get(0).getStatus());
    }

    @Test
    void testRecordDecisionWithRejection() {
        // Given
        when(issueMapper.selectById(1L)).thenReturn(testIssue);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(Collections.emptyList());

        // When
        decisionService.recordDecision(1L, "REJECTED", null, "test rejection", 1L, 0);

        // Then
        verify(recordMapper).insert(argThat(r -> "REJECTED".equals(r.getDecisionCode())));
        verify(issueMapper).update(argThat(i -> "RESOLVED".equals(i.getStatus())));
    }

    @Test
    void testRecordDecisionWithOverride() {
        // Given
        when(issueMapper.selectById(1L)).thenReturn(testIssue);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(Collections.emptyList());

        // When
        decisionService.recordDecision(1L, "OVERRIDE", null, "manual override", 1L, 0);

        // Then
        verify(recordMapper).insert(argThat(r -> "OVERRIDE".equals(r.getDecisionCode())));
    }

    @Test
    void testRecordDecisionWithRevisionConflict() {
        // Given
        when(issueMapper.selectById(1L)).thenReturn(testIssue);

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            decisionService.recordDecision(1L, "APPROVED", null, "test", 1L, 999);
        });
    }

    @Test
    void batchConfirmationOnlyAcceptsOpenInventoryIssuesFromSameBatch() {
        when(issueMapper.selectById(1L)).thenReturn(testIssue);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(Collections.emptyList());
        decisionService.batchResolveIssues(1L, List.of(1L), "MANUAL_CONFIRM", "按零库存计算", 7L);
        verify(recordMapper).insert(argThat(record -> "按零库存计算".equals(record.getReason())
                && "MANUAL_CONFIRM".equals(record.getDecisionCode())));
        assertEquals("READY_TO_CALCULATE", testBatch.getStatus());
    }

    @Test
    void batchConfirmationRejectsDifferentIssueType() {
        testIssue.setIssueType("MATERIAL_MISSING");
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(issueMapper.selectById(1L)).thenReturn(testIssue);
        assertThrows(IllegalArgumentException.class, () -> decisionService.batchResolveIssues(
                1L, List.of(1L), "MANUAL_CONFIRM", "按零库存计算", 7L));
        verify(recordMapper, never()).insert(any());
    }
}
