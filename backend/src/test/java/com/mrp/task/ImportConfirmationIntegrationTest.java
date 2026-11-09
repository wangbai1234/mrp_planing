package com.mrp.task;

import com.mrp.BaseIntegrationTest;
import com.mrp.common.security.CurrentUser;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.service.ForecastImportService;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.inventory.domain.InventoryDetail;
import com.mrp.inventory.domain.InventoryItem;
import com.mrp.inventory.service.InventoryImportService;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.task.repository.ImportTaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real MySQL transactions; validated staging fixtures do not change material or BOM data. */
class ImportConfirmationIntegrationTest extends BaseIntegrationTest {
    enum Flow { FORECAST, ITEMS, LEGACY }
    @Autowired ForecastImportService forecast;
    @Autowired InventoryImportService inventory;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MrpCalcBatchMapper batches;
    @SpyBean ImportTaskMapper tasks;
    Long owner;
    private static final String ITEM_WAREHOUSE = "CONFIRM-ITEM-WH";

    @BeforeEach void setup() {
        owner = insert("INSERT INTO user(username,password_hash) VALUES(?,'test-only')", "confirm-" + UUID.randomUUID());
        jdbc.update("INSERT INTO material(material_code,material_name,is_active,is_deleted) VALUES('CONFIRM-TEST','确认测试物料',1,0)");
        jdbc.update("INSERT INTO factory_warehouse_mapping(factory_code,warehouse_code,warehouse_name,is_good_warehouse,warehouse_attribute,quality_configured,enabled) VALUES('爱培科',?,'确认测试仓',1,'自有仓',1,1)", ITEM_WAREHOUSE);
        login();
    }

    @AfterEach void cleanup() {
        reset(tasks);
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM mrp_calc_batch WHERE created_by=?", owner);
        jdbc.update("UPDATE forecast_version_head SET current_version_id=NULL WHERE current_version_id IN (SELECT id FROM forecast_version WHERE created_by=?)", owner);
        jdbc.update("DELETE FROM forecast_detail WHERE version_id IN (SELECT id FROM forecast_version WHERE created_by=?)", owner);
        jdbc.update("DELETE FROM forecast_version WHERE created_by=?", owner);
        for (String table : List.of("inventory_item", "inventory_detail"))
            jdbc.update("DELETE FROM " + table + " WHERE snapshot_id IN (SELECT id FROM inventory_snapshot WHERE created_by=?)", owner);
        jdbc.update("DELETE FROM inventory_snapshot WHERE created_by=?", owner);
        jdbc.update("DELETE FROM import_task WHERE created_by=?", owner);
        jdbc.update("DELETE FROM factory_warehouse_mapping WHERE warehouse_code=?", ITEM_WAREHOUSE);
        jdbc.update("DELETE FROM material WHERE material_code='CONFIRM-TEST'");
        jdbc.update("DELETE FROM user WHERE id=?", owner);
    }

    @Test
    void newForecastInvalidatesEveryUnpublishedBatchStateAndPreservesHistory() {
        long previous = confirm(Flow.FORECAST, task(Flow.FORECAST));
        long intermediate = confirm(Flow.FORECAST, task(Flow.FORECAST));
        var statuses = List.of("DRAFT", "SCHEDULE_READY", "MATERIAL_REVIEW",
                "MATERIAL_RECALC_REQUIRED", "CALCULATING", "WAITING_DAILY_DISTRIBUTION",
                "WRITING", "READY", "PUBLISHED", "STALE");
        var ids = new java.util.LinkedHashMap<Long, String>();
        for (String status : statuses) {
            long id = insert("INSERT INTO mrp_calc_batch(factory_code,status,forecast_version_id,version_no,created_by,active_request_key) VALUES('爱培科',?,?,1,?,?)",
                    status, previous, owner, "forecast-stale-" + UUID.randomUUID());
            ids.put(id, status);
        }
        long latestBatch = insert("INSERT INTO mrp_calc_batch(factory_code,status,forecast_version_id,version_no,created_by,active_request_key) VALUES('爱培科','SCHEDULE_READY',?,1,?,?)",
                intermediate, owner, "forecast-stale-" + UUID.randomUUID());
        long next = confirm(Flow.FORECAST, task(Flow.FORECAST));
        assertNotEquals(previous, next);
        assertEquals("STALE", batches.selectById(latestBatch).getStatus());
        for (var entry : ids.entrySet()) {
            String expected = List.of("PUBLISHED", "STALE").contains(entry.getValue()) ? entry.getValue() : "STALE";
            assertEquals(expected, batches.selectById(entry.getKey()).getStatus(), entry.getValue());
            if ("STALE".equals(expected)) {
                assertEquals(0, batches.updateStatusForce(entry.getKey(), "READY"));
                assertEquals(0, batches.activateRunAndStatus(entry.getKey(), 99L, "MATERIAL_REVIEW"));
                assertEquals("STALE", batches.selectById(entry.getKey()).getStatus());
            }
        }
        assertEquals(next, jdbc.queryForObject("SELECT current_version_id FROM forecast_version_head WHERE id=1", Long.class));
        assertEquals("PUBLISHED", batches.selectCurrent().getStatus());
    }

    @ParameterizedTest @EnumSource(Flow.class)
    void successfulRetryWorksWithoutStagingAndDoesNotCreateAnotherVersion(Flow flow) {
        long task = task(flow);
        long first = confirm(flow, task);
        assertFalse(cache(flow).containsKey(task));
        assertEquals(first, confirm(flow, task));
        stage(flow, task); // Same-file upload can repopulate a successful task's preview.
        assertEquals(first, confirm(flow, task));
        assertEquals(1, formalCount(flow));
        assertEquals(1, jdbc.queryForObject("SELECT version FROM import_task WHERE id=?", Integer.class, task));
    }

    @ParameterizedTest @EnumSource(Flow.class)
    void concurrentConfirmationsReturnTheSameCommittedResult(Flow flow) throws Exception {
        long task = task(flow);
        var entered = new CountDownLatch(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Long>>();
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM import_task WHERE id=? FOR UPDATE", Long.class, task);
                for (int i = 0; i < 2; i++) futures.add(workers.submit(() -> {
                    login(); entered.countDown();
                    try { return confirm(flow, task); }
                    finally { SecurityContextHolder.clearContext(); }
                }));
                try { assertTrue(entered.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            });
            long first = futures.get(0).get(15, TimeUnit.SECONDS);
            assertEquals(first, futures.get(1).get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, formalCount(flow));
        assertEquals(1, jdbc.queryForObject("SELECT version FROM import_task WHERE id=?", Integer.class, task));
    }

    @ParameterizedTest @EnumSource(Flow.class)
    void outerTransactionRollbackKeepsStagingAndPendingTask(Flow flow) {
        long task = task(flow);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            confirm(flow, task);
            throw new IllegalStateException("force outer transaction rollback");
        }));
        assertEquals(0, formalCount(flow));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM import_task WHERE id=?", String.class, task));
        assertTrue(cache(flow).containsKey(task), "A rolled back confirmation must remain retryable");
        confirm(flow, task);
        assertEquals(1, formalCount(flow));
    }

    @ParameterizedTest @EnumSource(Flow.class)
    void terminalFailureCannotBeConfirmedEvenWithValidStaging(Flow flow) {
        long task = task(flow);
        jdbc.update("UPDATE import_task SET status='FAILED' WHERE id=?", task);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> confirm(flow, task)).getStatusCode().value());
        assertEquals(0, formalCount(flow));
    }

    @ParameterizedTest @EnumSource(Flow.class)
    void successfulTaskWithMissingResourceCannotCreateAReplacement(Flow flow) {
        long task = task(flow);
        jdbc.update("UPDATE import_task SET status='SUCCEEDED',result_resource_id=9223372036854775806 WHERE id=?", task);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> confirm(flow, task)).getStatusCode().value());
        assertEquals(0, formalCount(flow));
    }

    @ParameterizedTest @EnumSource(Flow.class)
    void rejectedCompletionRollsBackAllFormalRowsAndKeepsStaging(Flow flow) {
        long task = task(flow);
        doReturn(0).when(tasks).completePending(eq(task), anyLong(), anyInt());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> confirm(flow, task)).getStatusCode().value());
        assertEquals(0, formalCount(flow));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM import_task WHERE id=?", String.class, task));
        assertEquals(0, jdbc.queryForObject("SELECT version FROM import_task WHERE id=?", Integer.class, task));
        assertTrue(cache(flow).containsKey(task));
        reset(tasks);
        confirm(flow, task);
        assertEquals(1, formalCount(flow));
    }

    @ParameterizedTest @EnumSource(Flow.class)
    void completionSqlRejectsStaleVersionAndNonPendingTask(Flow flow) {
        long task = task(flow);
        assertEquals(0, tasks.completePending(task, 123L, 1));
        for (String status : List.of("FAILED", "CANCELLED", "RUNNING", "SUCCEEDED")) {
            jdbc.update("UPDATE import_task SET status=? WHERE id=?", status, task);
            assertEquals(0, tasks.completePending(task, 123L, 0));
            assertEquals(status, jdbc.queryForObject("SELECT status FROM import_task WHERE id=?", String.class, task));
        }
        assertEquals(0, formalCount(flow));
    }

    @org.junit.jupiter.api.Test
    void itemConfirmationRejectsMaterialRemovedAfterPreviewWithoutWritingSnapshot() {
        long task = task(Flow.ITEMS);
        jdbc.update("UPDATE material SET is_deleted=1 WHERE material_code='CONFIRM-TEST'");
        assertEquals(0, formalCount(Flow.ITEMS));
        assertThrows(com.mrp.common.exception.ValidationException.class, () -> inventory.confirmItem(task, owner));
        assertEquals(0, formalCount(Flow.ITEMS));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM import_task WHERE id=?", String.class, task));
    }

    @Test
    void itemConfirmationRejectsWarehouseQualityChangedAfterPreviewWithoutWritingSnapshot() {
        long task = task(Flow.ITEMS);
        @SuppressWarnings("unchecked")
        ParseResult<InventoryItem> staged = (ParseResult<InventoryItem>) cache(Flow.ITEMS).get(task);
        cache(Flow.ITEMS).put(task, new ParseResult<>(staged.rows(), staged.errors(), staged.recognizedMonths(),
                staged.totalRows(), staged.successRows(), staged.errorRows(), staged.rawRows(),
                List.of("仓库编码*", "仓库名称", "库存分类", "料号*", "供应商名称", "库存数量*", "备注")));
        jdbc.update("UPDATE factory_warehouse_mapping SET is_good_warehouse=0 WHERE warehouse_code=?", ITEM_WAREHOUSE);
        assertEquals(0, formalCount(Flow.ITEMS));
        var error = assertThrows(com.mrp.common.exception.ValidationException.class,
                () -> inventory.confirmItem(task, owner));
        assertTrue(error.getMessage().contains("仓库品质配置已变化"));
        assertEquals(0, formalCount(Flow.ITEMS));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM import_task WHERE id=?", String.class, task));
    }

    private long task(Flow flow) {
        String prefix = switch (flow) { case FORECAST -> "forecast:"; case ITEMS -> "inventory_item:"; case LEGACY -> "inventory:"; };
        long id = insert("INSERT INTO import_task(task_type,request_key,status,created_by) VALUES(?,?,'PENDING',?)",
                flow == Flow.FORECAST ? "FORECAST" : "INVENTORY", prefix + UUID.randomUUID(), owner);
        stage(flow, id);
        return id;
    }

    private void stage(Flow flow, long task) {
        Object row = switch (flow) {
            case FORECAST -> new ForecastDetail(null, null, "爱培科", "CONFIRM-TEST", "fixture", null, null, null,
                    null, null, null, LocalDate.of(2026, 10, 1), new BigDecimal("123.000001"));
            case LEGACY -> new InventoryDetail(null, null, "爱培科", "CONFIRM-TEST", "test", "test", "GOOD", 123L, true);
            case ITEMS -> new InventoryItem(null, null, "test", "test", "CONFIRM-TEST", "GOOD", ITEM_WAREHOUSE, "确认测试仓", null, "fixture",
                    "test", null, new BigDecimal("123.000001"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("123.000001"), null, null, null, BigDecimal.ZERO, null, null, null);
        };
        cache(flow).put(task, new ParseResult<>(List.of(row), List.of(), List.of("2026-10"), 1, 1, 0, List.of(), List.of()));
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Object> cache(Flow flow) {
        return (Map<Long, Object>) Objects.requireNonNull(ReflectionTestUtils.getField(
                flow == Flow.FORECAST ? forecast : inventory, flow == Flow.ITEMS ? "itemStagingCache" : "stagingCache"));
    }
    private long confirm(Flow flow, long task) {
        return switch (flow) {
            case FORECAST -> forecast.confirm(task, owner).id();
            case ITEMS -> inventory.confirmItem(task, owner).getId();
            case LEGACY -> inventory.confirm(task, LocalDate.of(2026, 10, 17), owner).getId();
        };
    }
    private int formalCount(Flow flow) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + (flow == Flow.FORECAST ? "forecast_version" : "inventory_snapshot")
                + " WHERE created_by=?", Integer.class, owner);
    }
    private long insert(String sql, Object... values) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private void login() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser.UserPrincipal(owner, "confirm-test"), null, List.of()));
    }
}
