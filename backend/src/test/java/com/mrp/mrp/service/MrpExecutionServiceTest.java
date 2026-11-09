package com.mrp.mrp.service;

import com.mrp.mrp.domain.*;
import com.mrp.mrp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MrpExecutionServiceTest {

    @Mock
    private MrpDailyExecutionMapper executionMapper;
    @Mock
    private MrpDailyPlanDetailMapper dailyPlanMapper;
    @Mock
    private MrpCalcBatchMapper batchMapper;
    @Mock
    private MrpCalcExceptionMapper exceptionMapper;

    @Mock private DailyPlanningService dailyPlanning;

    @InjectMocks
    private MrpExecutionService executionService;

    private MrpCalcBatch testBatch;
    private MrpDailyPlanDetail testPlan;
    private MrpDailyExecution testExecution;

    @BeforeEach
    void setUp() {
        testBatch = new MrpCalcBatch();
        testBatch.setId(1L);
        testBatch.setStatus("READY");
        testBatch.setActiveRunId(1L);

        testPlan = new MrpDailyPlanDetail();
        testPlan.setId(1L);
        testPlan.setRunId(1L);
        testPlan.setBatchId(1L);
        testPlan.setRowKey("rowkey1");
        testPlan.setTerminalType("MACHINE_OUTPUT");
        testPlan.setFactoryCode("永惠");
        testPlan.setRootMaterialCode("A-1000");
        testPlan.setScheduleDate(LocalDate.now());
        testPlan.setSystemQuantity(BigDecimal.valueOf(100));

        testExecution = new MrpDailyExecution();
        testExecution.setId(1L);
        testExecution.setBatchId(1L);
        testExecution.setRowKey("rowkey1");
        testExecution.setScheduleDate(LocalDate.now());
        testExecution.setExecutionStatus("PLANNED");
        testExecution.setPlannedQuantity(BigDecimal.valueOf(100));
    }

    @Test
    void testConfirmExecution() {
        // Given
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(dailyPlanning.getDailyPlans(1L)).thenReturn(List.of(testPlan));
        when(executionMapper.selectByBatchRowDate(1L, "rowkey1", LocalDate.now())).thenReturn(null);

        // When
        MrpDailyExecution result = executionService.confirmExecution(1L, "rowkey1", BigDecimal.valueOf(100), 1L, "test");

        // Then
        assertNotNull(result);
        assertEquals("EXECUTED", result.getExecutionStatus());
        assertEquals(BigDecimal.valueOf(100), result.getActualQuantity());
        verify(executionMapper).insert(any());
    }

    @Test
    void testConfirmExecutionWithQuantityMismatch() {
        // Given
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(dailyPlanning.getDailyPlans(1L)).thenReturn(List.of(testPlan));
        when(executionMapper.selectByBatchRowDate(1L, "rowkey1", LocalDate.now())).thenReturn(null);

        // When
        MrpDailyExecution result = executionService.confirmExecution(1L, "rowkey1", BigDecimal.valueOf(80), 1L, "test");

        // Then
        assertNotNull(result);
        assertEquals("EXECUTED", result.getExecutionStatus());
        assertEquals(BigDecimal.valueOf(80), result.getActualQuantity());
        verify(exceptionMapper).insert(argThat(e -> 
            "EXECUTION_QUANTITY_MISMATCH".equals(e.getErrorCode())));
    }

    @Test
    void testConfirmExecutionOnLockedDate() {
        // Given
        testExecution.setExecutionStatus("LOCKED");
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(dailyPlanning.getDailyPlans(1L)).thenReturn(List.of(testPlan));
        when(executionMapper.selectByBatchRowDate(1L, "rowkey1", LocalDate.now())).thenReturn(testExecution);
        when(executionMapper.freezeStatus(eq(1L), eq("EXECUTED"), any(), any(), any(), any(), any())).thenReturn(1);

        // A frozen plan can receive actual production without unlocking the date.
        executionService.confirmExecution(1L, "rowkey1", BigDecimal.valueOf(100), 1L, "test");
        verify(executionMapper).freezeStatus(eq(1L), eq("EXECUTED"), eq(BigDecimal.valueOf(100)),
                eq(BigDecimal.valueOf(100)), eq(1L), eq("test"), eq(testExecution.getRevision()));
    }

    @Test
    void testConfirmExecutionWithInvalidRowKey() {
        // Given
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(dailyPlanning.getDailyPlans(1L)).thenReturn(List.of());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            executionService.confirmExecution(1L, "invalid-key", BigDecimal.valueOf(100), 1L, "test");
        });
    }

    @Test
    void testLockDay() {
        // Given
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(dailyPlanning.getDailyPlans(1L, LocalDate.now(), LocalDate.now()))
            .thenReturn(List.of(testPlan));
        when(executionMapper.selectByBatchRowDate(1L, "rowkey1", LocalDate.now())).thenReturn(null);

        // When
        executionService.lockDay(1L, LocalDate.now());

        // Then
        verify(executionMapper).insert(argThat(e -> 
            "LOCKED".equals(e.getExecutionStatus())));
    }

    @Test
    void testUnlockDay() {
        // Given
        testExecution.setExecutionStatus("LOCKED");
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(executionMapper.selectByBatchId(1L)).thenReturn(List.of(testExecution));

        // When
        executionService.unlockDay(1L, LocalDate.now());

        // Then
        verify(executionMapper).updateStatus(eq(1L), eq("PLANNED"), isNull(), isNull(), isNull(), any());
    }

    @Test
    void testUnlockDayOnExecutedDate() {
        // Given
        testExecution.setExecutionStatus("EXECUTED");
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(executionMapper.selectByBatchId(1L)).thenReturn(List.of(testExecution));

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            executionService.unlockDay(1L, LocalDate.now());
        });
    }

    @Test
    void testGetExecutionStatus() {
        // Given
        when(executionMapper.selectByBatchId(1L)).thenReturn(List.of(testExecution));
        when(dailyPlanning.getDailyPlans(1L)).thenReturn(List.of(testPlan));

        // When
        Map<String, Object> result = executionService.getExecutionStatus(1L);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.get("batchId"));
        assertEquals(1, result.get("totalRecords"));
        assertEquals(1L, result.get("planned"));
        assertEquals(0L, result.get("locked"));
        assertEquals(0L, result.get("executed"));
    }

    @Test
    void publishedPlanWithoutExecutionRecordIsVisible() {
        when(executionMapper.selectByBatchId(1L)).thenReturn(List.of());
        when(dailyPlanning.getDailyPlans(1L)).thenReturn(List.of(testPlan));

        Map<String, Object> result = executionService.getExecutionStatus(1L);

        assertEquals(1, result.get("totalRecords"));
        assertEquals(1L, result.get("planned"));
        MrpDailyExecution row = ((List<MrpDailyExecution>) result.get("records")).get(0);
        assertEquals("PLANNED", row.getExecutionStatus());
        assertEquals("A-1000", row.getRootMaterialCode());
        assertEquals("永惠", row.getFactoryCode());
    }

    @Test
    void executionPageUsesActiveRunAndServerSideFilters() {
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        LocalDate date = LocalDate.of(2026, 10, 29);
        when(executionMapper.countPage(1L, 1L, "A-10", date)).thenReturn(21);
        when(executionMapper.selectPage(1L, 1L, "A-10", date, 20, 20))
                .thenReturn(List.of(testExecution));

        var result = executionService.getExecutionPage(1L, " A-10 ", date, 2, 20);

        assertEquals(21, result.total());
        assertEquals(2, result.totalPages());
        assertEquals(2, result.page());
        assertEquals(List.of(testExecution), result.items());
        verify(dailyPlanning, never()).getDailyPlans(anyLong());
    }

    @Test
    void executionPageRejectsInvalidSizeAndReturnsEmptyForNoActiveRun() {
        assertThrows(IllegalArgumentException.class,
                () -> executionService.getExecutionPage(1L, "", null, 1, 101));
        testBatch.setActiveRunId(null);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        var result = executionService.getExecutionPage(1L, "", null, 1, 20);
        assertEquals(0, result.total());
        assertTrue(result.items().isEmpty());
        verifyNoInteractions(executionMapper);
    }

    @Test
    void testIsDateLockedOrExecuted() {
        // Given
        testExecution.setExecutionStatus("LOCKED");
        when(executionMapper.selectByBatchId(1L)).thenReturn(List.of(testExecution));

        // When
        boolean result = executionService.isDateLockedOrExecuted(1L, LocalDate.now());

        // Then
        assertTrue(result);
    }

    @Test
    void testIsDateNotLockedOrExecuted() {
        // Given
        testExecution.setExecutionStatus("PLANNED");
        when(executionMapper.selectByBatchId(1L)).thenReturn(List.of(testExecution));

        // When
        boolean result = executionService.isDateLockedOrExecuted(1L, LocalDate.now());

        // Then
        assertFalse(result);
    }

    @Test
    void testConfirmExecutionOnAlreadyExecutedDate() {
        // Given
        testExecution.setExecutionStatus("EXECUTED");
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(dailyPlanning.getDailyPlans(1L)).thenReturn(List.of(testPlan));
        when(executionMapper.selectByBatchRowDate(1L, "rowkey1", LocalDate.now())).thenReturn(testExecution);

        // When
        // Then an executed fact is immutable; correction must be a separate business flow.
        assertThrows(IllegalStateException.class,
                () -> executionService.confirmExecution(1L, "rowkey1", BigDecimal.valueOf(100), 1L, "test"));
        verify(executionMapper, never()).updateStatus(anyLong(), anyString(), any(), any(), any(), any());
    }
}
