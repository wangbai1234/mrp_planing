package com.mrp.mrp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.repository.CalcTaskMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MrpLifecycleTaskServiceTest {
    @Test void cancelledRevisionAllowsANewTaskAndRepeatedSubmissionReusesIt() {
        var tasks = mock(CalcTaskMapper.class);
        var batches = mock(MrpCalcBatchMapper.class);
        var service = new MrpLifecycleTaskService(tasks, batches, new ObjectMapper(), mock(PlanningReleaseValidationService.class));
        var source = new MrpCalcBatch(); source.setStatus("PUBLISHED");
        var cancelled = new MrpCalcBatch(); cancelled.setStatus("CANCELLED"); cancelled.setRevisionOfBatchId(42L);
        when(batches.selectForUpdate(42L)).thenReturn(source);
        when(batches.selectById(43L)).thenReturn(cancelled);
        var succeeded = new CalcTask(); succeeded.setId(8L); succeeded.setStatus("SUCCEEDED"); succeeded.setResultResourceId(43L);
        var pending = new CalcTask(); pending.setId(9L); pending.setStatus("PENDING");
        when(tasks.selectLatestLifecycleForBatch(42L, MrpLifecycleTaskService.REVISION, "mrp-revision-")).thenReturn(succeeded, pending);
        doAnswer(invocation -> { ((CalcTask) invocation.getArgument(0)).setId(9L); return null; }).when(tasks).insert(any());
        when(tasks.selectById(9L)).thenReturn(pending);

        var created = service.submit(42L, MrpLifecycleTaskService.REVISION, null, 7L);
        assertEquals("MRP_REVISION", created.getTaskType());
        assertEquals("PENDING", created.getStatus());
        assertEquals(created.getId(), service.submit(42L, MrpLifecycleTaskService.REVISION, null, 7L).getId());
        verify(tasks, times(1)).insert(any());
    }

    @Test void successfulRevisionStillInUseRemainsIdempotent() {
        var tasks = mock(CalcTaskMapper.class);
        var batches = mock(MrpCalcBatchMapper.class);
        var service = new MrpLifecycleTaskService(tasks, batches, new ObjectMapper(), mock(PlanningReleaseValidationService.class));
        var source = new MrpCalcBatch(); source.setStatus("PUBLISHED");
        var revision = new MrpCalcBatch(); revision.setStatus("READY"); revision.setRevisionOfBatchId(42L);
        when(batches.selectForUpdate(42L)).thenReturn(source);
        when(batches.selectById(43L)).thenReturn(revision);
        var succeeded = new CalcTask(); succeeded.setId(8L); succeeded.setStatus("SUCCEEDED"); succeeded.setResultResourceId(43L);
        when(tasks.selectLatestLifecycleForBatch(42L, MrpLifecycleTaskService.REVISION, "mrp-revision-")).thenReturn(succeeded);
        when(tasks.selectById(8L)).thenReturn(succeeded);

        assertEquals(8L, service.submit(42L, MrpLifecycleTaskService.REVISION, null, 7L).getId());
        verify(tasks, never()).insert(any());
    }

    @Test void submittingTheSamePublishTwiceReturnsTheExistingPendingTask() {
        var tasks = mock(CalcTaskMapper.class);
        var batches = mock(MrpCalcBatchMapper.class);
        var service = new MrpLifecycleTaskService(tasks, batches, new ObjectMapper(), mock(PlanningReleaseValidationService.class));
        var batch = new MrpCalcBatch(); batch.setStatus("READY"); batch.setInputChecksum("frozen");
        when(batches.selectForUpdate(42L)).thenReturn(batch);
        var pending = new CalcTask(); pending.setStatus("PENDING");
        when(tasks.selectLatestLifecycleForBatch(42L, MrpLifecycleTaskService.PUBLISH, "mrp-publish-")).thenReturn(null, pending);
        when(tasks.selectById(null)).thenReturn(pending);
        var first = service.submit(42L, MrpLifecycleTaskService.PUBLISH, "proof", 7L);
        assertEquals("MRP_PUBLISH", first.getTaskType());
        assertTrue(first.getInputPayload().contains("proof"));
        assertEquals(first.getId(), service.submit(42L, MrpLifecycleTaskService.PUBLISH, "proof", 7L).getId());
        verify(tasks, times(1)).insert(any(CalcTask.class));
    }

    @Test void revisionRequiresPublishedSourceAndNeverQueuesInvalidBatch() {
        var tasks = mock(CalcTaskMapper.class);
        var batches = mock(MrpCalcBatchMapper.class);
        var service = new MrpLifecycleTaskService(tasks, batches, new ObjectMapper(), mock(PlanningReleaseValidationService.class));
        var batch = new MrpCalcBatch(); batch.setStatus("READY");
        when(batches.selectForUpdate(7L)).thenReturn(batch);
        assertThrows(IllegalStateException.class, () -> service.submit(7L, MrpLifecycleTaskService.REVISION, null, 3L));
        verify(tasks, never()).insert(any());
    }
    @Test void unresolvedBadgesNeverCreateAPublishTaskEvenWithCapacityAcknowledgment() {
        var tasks = mock(CalcTaskMapper.class);
        var batches = mock(MrpCalcBatchMapper.class);
        var validation = mock(PlanningReleaseValidationService.class);
        var service = new MrpLifecycleTaskService(tasks, batches, new ObjectMapper(), validation);
        var batch = new MrpCalcBatch(); batch.setStatus("READY");
        when(batches.selectForUpdate(42L)).thenReturn(batch);
        doThrow(new IllegalStateException("默认下钻待确认 24 项")).when(validation).requirePublishable(batch);
        assertTrue(assertThrows(IllegalStateException.class,
                () -> service.submit(42L, MrpLifecycleTaskService.PUBLISH, "ack", 7L)).getMessage().contains("24"));
        verify(tasks, never()).insert(any());
    }

}
