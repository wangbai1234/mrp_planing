package com.mrp.mrp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.repository.CalcTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MrpLifecycleWorkerServiceTest {
    @Test void committedRevisionProducesResultTaskAndStaleChecksumFailsBeforeWriting() {
        var tasks = mock(CalcTaskMapper.class);
        var batches = mock(MrpCalcBatchMapper.class);
        var orchestrator = mock(BatchOrchestratorService.class);
        var progress = mock(TaskProgressService.class);
        when(progress.listener(anyLong(), anyString(), anyInt())).thenReturn(com.mrp.mrp.engine.CalculationProgressListener.NONE);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var worker = new MrpLifecycleWorkerService(tasks, batches, orchestrator, progress, new ObjectMapper(), manager);
        var pending = new CalcTask(); pending.setId(8L); pending.setVersion(0);
        var claimed = new CalcTask(); claimed.setId(8L); claimed.setVersion(1); claimed.setTaskType(MrpLifecycleTaskService.REVISION);
        claimed.setInputChecksum("frozen"); claimed.setInputPayload("{\"batchId\":17}"); claimed.setCreatedBy(5L);
        var source = new MrpCalcBatch(); source.setInputChecksum("frozen");
        var result = new MrpCalcBatch(); result.setId(19L);
        when(tasks.claimTask(eq(8L), anyString(), eq(5), eq(0))).thenReturn(1);
        when(tasks.selectById(8L)).thenReturn(claimed);
        when(batches.selectById(17L)).thenReturn(source);
        when(orchestrator.revise(eq(17L), eq(5L), any())).thenReturn(result);
        when(tasks.finishOwnedLifecycle(eq(8L), anyString(), eq(1), eq(19L))).thenReturn(1);
        assertTrue(worker.run(pending));
        verify(orchestrator).revise(eq(17L), eq(5L), any());
        verify(tasks).finishOwnedLifecycle(eq(8L), anyString(), eq(1), eq(19L));

        source.setInputChecksum("changed");
        assertFalse(worker.run(pending));
        verify(orchestrator, times(1)).revise(eq(17L), eq(5L), any());
        verify(tasks).failOwnedTask(eq(8L), anyString(), eq(1), contains("输入版本已变化"));
    }

    @Test void losingTaskOwnershipRollsBackBusinessTransaction() {
        var tasks = mock(CalcTaskMapper.class);
        var batches = mock(MrpCalcBatchMapper.class);
        var orchestrator = mock(BatchOrchestratorService.class);
        var progress = mock(TaskProgressService.class);
        when(progress.listener(anyLong(), anyString(), anyInt())).thenReturn(com.mrp.mrp.engine.CalculationProgressListener.NONE);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var worker = new MrpLifecycleWorkerService(tasks, batches, orchestrator, progress, new ObjectMapper(), manager);
        var pending = new CalcTask(); pending.setId(8L); pending.setVersion(0);
        var claimed = new CalcTask(); claimed.setId(8L); claimed.setVersion(1);
        claimed.setTaskType(MrpLifecycleTaskService.REVISION); claimed.setInputPayload("{\"batchId\":17}");
        var source = new MrpCalcBatch(); var result = new MrpCalcBatch(); result.setId(19L);
        when(tasks.claimTask(eq(8L), anyString(), eq(5), eq(0))).thenReturn(1);
        when(tasks.selectById(8L)).thenReturn(claimed); when(batches.selectById(17L)).thenReturn(source);
        when(orchestrator.revise(eq(17L), isNull(), any())).thenReturn(result);
        assertFalse(worker.run(pending));
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
    }
}
