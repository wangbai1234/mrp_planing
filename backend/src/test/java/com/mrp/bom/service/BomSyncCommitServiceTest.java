package com.mrp.bom.service;

import com.mrp.bom.domain.BomDetail;
import com.mrp.bom.local.repository.BomSyncTaskMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BomSyncCommitServiceTest {
    private final BomService bom = mock(BomService.class);
    private final BomSyncTaskMapper tasks = mock(BomSyncTaskMapper.class);
    private final BomSyncCommitService service = new BomSyncCommitService(bom, tasks);

    @Test
    void syncFinishesAfterReplacingCurrentShanghaiBom() {
        when(tasks.isOwned(4L, "worker")).thenReturn(1);
        when(bom.replaceLocalData(anyList())).thenReturn(Map.of(
                "parentCount", 2, "validation", Map.of(
                        "zeroQty", 0, "selfReference", 0, "isValid", true)));
        when(tasks.finish(4L, "worker", 2L, 0L, 0L, true)).thenReturn(1);

        service.replaceAndFinish(4L, "worker", List.of(new BomDetail()));

        var order = inOrder(bom, tasks);
        order.verify(bom).replaceLocalData(anyList());
        order.verify(tasks).finish(4L, "worker", 2L, 0L, 0L, true);
    }

    @Test
    void failedReplacementLeavesSyncUnfinishedForWorkerToMarkFailed() {
        when(tasks.isOwned(4L, "worker")).thenReturn(1);
        when(bom.replaceLocalData(anyList())).thenThrow(new IllegalStateException("BOM无效"));

        assertThrows(IllegalStateException.class,
                () -> service.replaceAndFinish(4L, "worker", List.of(new BomDetail())));
        verify(tasks, never()).finish(anyLong(), anyString(), anyLong(), anyLong(), anyLong(), anyBoolean());
    }
}
