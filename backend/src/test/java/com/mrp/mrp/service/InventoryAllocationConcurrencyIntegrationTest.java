package com.mrp.mrp.service;

import com.mrp.BaseIntegrationTest;
import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real MySQL transactions verify that allocation writes serialize with batch precheck. */
class InventoryAllocationConcurrencyIntegrationTest extends BaseIntegrationTest {
    @Autowired InventoryNormalizationService allocations;
    @Autowired MrpCalcBatchMapper batches;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;

    @Test void resetWaitsForBatchLockAndRejectsCalculatedState() throws Exception {
        long batch = insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no) VALUES('永惠','WAITING_DECISION',991001)");
        long pool = insert("INSERT INTO mrp_inventory_pool(batch_id,snapshot_id,factory_code,inventory_org,material_code,available_qty,reserved_qty,allocated_qty,version) VALUES(" + batch + ",1,'永惠','YONGHUI','LOCK-TEST',500,50,400,0)");
        long allocation = insert("INSERT INTO mrp_inventory_allocation(pool_id,demand_key,root_material_code,source_month,gross_qty,allocated_qty,allocation_type,manual_confirmed,revision) VALUES(" + pool + ",'LOCK-TEST','ROOT','2026-10-01',600,400,'MANUAL_OVERRIDE',1,1)");
        var lockHeld = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var precheck = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                batches.selectForUpdate(batch);
                lockHeld.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("lock timeout"); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
                batches.updateStatusForce(batch, "READY");
                return null;
            }));
            assertTrue(lockHeld.await(10, TimeUnit.SECONDS));
            var reset = executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        new CurrentUser.UserPrincipal(9L, "allocation-test"), null, List.of()));
                try { allocations.resetAllocation(allocation); return "unexpected success"; }
                catch (IllegalStateException rejected) { return rejected.getMessage(); }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertFalse(reset.isDone());
            release.countDown();
            precheck.get(15, TimeUnit.SECONDS);
            assertTrue(reset.get(15, TimeUnit.SECONDS).contains("不允许恢复"));
            assertEquals("READY", jdbc.queryForObject("SELECT status FROM mrp_calc_batch WHERE id=?", String.class, batch));
            assertEquals(0, jdbc.queryForObject("SELECT allocated_qty FROM mrp_inventory_allocation WHERE id=?", BigDecimal.class, allocation).compareTo(new BigDecimal("400")));
            assertEquals(0, jdbc.queryForObject("SELECT allocated_qty FROM mrp_inventory_pool WHERE id=?", BigDecimal.class, pool).compareTo(new BigDecimal("400")));
        } finally {
            release.countDown();
            executor.shutdownNow();
            jdbc.update("DELETE FROM mrp_inventory_allocation WHERE id=?", allocation);
            jdbc.update("DELETE FROM mrp_inventory_pool WHERE id=?", pool);
            jdbc.update("DELETE FROM mrp_calc_batch WHERE id=?", batch);
        }
    }

    private long insert(String sql) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
