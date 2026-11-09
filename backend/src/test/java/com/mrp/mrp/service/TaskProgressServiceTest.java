package com.mrp.mrp.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskProgressServiceTest {
    @Test void throttlesWithinStageButReportsBoundariesImmediatelyAndNeverRegresses() {
        AtomicLong now = new AtomicLong();
        List<String> writes = new ArrayList<>();
        var progress = new TaskProgressService.ThrottledProgress(
                (percent, stage, done, total) -> writes.add(percent + ":" + stage + ":" + done), now::get);
        progress.report(10, "展开", 0, 100);
        for (int i = 1; i <= 20; i++) progress.report(10 + i, "展开", i, 100);
        assertEquals(List.of("10:展开:0"), writes);
        now.set(1_000_000_000L);
        progress.report(31, "展开", 21, 100);
        progress.report(20, "保存", 0, 100);
        progress.report(100, "确认", 100, 100);
        assertEquals(List.of("10:展开:0", "31:展开:21", "31:保存:0", "99:确认:100"), writes);
    }

    @Test void progressPersistenceFailureDoesNotAbortCalculationAndLaterUpdatesRecover() {
        AtomicLong now = new AtomicLong();
        List<Integer> writes = new ArrayList<>();
        var progress = new TaskProgressService.ThrottledProgress((percent, stage, done, total) -> {
            if (percent == 10) throw new IllegalStateException("progress store unavailable");
            writes.add(percent);
        }, now::get);
        assertDoesNotThrow(() -> progress.report(10, "展开", 0, 2));
        now.set(1_000_000_000L);
        progress.report(20, "展开", 1, 2);
        assertEquals(List.of(20), writes);
    }
}
