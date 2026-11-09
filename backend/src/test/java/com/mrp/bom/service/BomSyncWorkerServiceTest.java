package com.mrp.bom.service;

import com.mrp.bom.domain.BomDetail;
import com.mrp.bom.domain.BomSyncTask;
import com.mrp.bom.local.repository.BomSyncTaskMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiConsumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BomSyncWorkerServiceTest {
    @Test
    void failedNcPullMarksTaskFailedWithoutReplacingLocalBom() {
        BomSyncTaskMapper tasks = mock(BomSyncTaskMapper.class);
        BomService bom = mock(BomService.class);
        BomSyncCommitService commit = mock(BomSyncCommitService.class);
        BomSyncTask pending = new BomSyncTask();
        pending.setId(7L);
        when(tasks.selectPending()).thenReturn(pending);
        when(tasks.claim(eq(7L), anyString())).thenReturn(1);
        when(bom.fetchFromOracle(any())).thenThrow(new IllegalStateException("NC unavailable"));

        new BomSyncWorkerService(tasks, bom, commit).poll();

        verify(tasks).fail(eq(7L), anyString(), eq("NC unavailable"));
        verifyNoInteractions(commit);
    }

    @Test
    void persistsPulledCountBeforeCommitting() {
        BomSyncTaskMapper tasks = mock(BomSyncTaskMapper.class);
        BomService bom = mock(BomService.class);
        BomSyncCommitService commit = mock(BomSyncCommitService.class);
        BomSyncTask pending = new BomSyncTask();
        pending.setId(8L);
        BomDetail detail = new BomDetail();
        when(tasks.selectPending()).thenReturn(pending);
        when(tasks.claim(eq(8L), anyString())).thenReturn(1);
        when(tasks.updateProgress(eq(8L), anyString(), anyLong(), anyLong(), anyString())).thenReturn(1);
        when(bom.fetchFromOracle(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            BiConsumer<Integer, Integer> progress = invocation.getArgument(0);
            progress.accept(1, 2);
            progress.accept(2, 2);
            return List.of(detail, detail);
        });

        new BomSyncWorkerService(tasks, bom, commit).poll();

        var order = inOrder(tasks, commit);
        order.verify(tasks).updateProgress(eq(8L), anyString(), eq(1L), eq(2L), eq("从NC拉取BOM"));
        order.verify(tasks).updateProgress(eq(8L), anyString(), eq(2L), eq(2L), eq("从NC拉取BOM"));
        order.verify(tasks).updateProgress(eq(8L), anyString(), eq(2L), eq(2L), eq("保存本地BOM"));
        order.verify(commit).replaceAndFinish(eq(8L), anyString(), anyList());
    }
}
