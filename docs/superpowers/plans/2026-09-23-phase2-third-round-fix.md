# MRP Phase 2 第三轮修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all P0-301 through P0-309 and P1-301 through P1-303 issues from the Phase 2 third round verification report, establishing a publishable Phase 2闭环.

**Architecture:** The fixes follow the repair manual's recommended order: issue blocking → run isolation → worker lease → input binding → inventory dimensions → execution/locking → publish/revise → BOM adjudication → permissions → frontend → UAT.

**Tech Stack:** Java 21, Spring Boot 3.x, MyBatis, Flyway, MySQL 8.x, Vue 3 + TypeScript + Element Plus

**Spec:** `docs/MRP Phase2第三轮验收报告与修复手册.md`

## Global Constraints

- Database migrations only via V028+; never modify V001-V027
- All quantities use `BigDecimal` / `DECIMAL(18,6)`; no `float`/`double`/`long`
- SecurityConfig, shared DTOs, workflow.yaml are exclusive files — only one Agent at a time
- Docker/Testcontainers environment may be unavailable; record as environment blocker, don't modify tech stack
- Phase 1 history read-only compatibility must be preserved

---

## Phase A: Issue Blocking & Run Isolation (P0-301, P0-302)

### Task A1: Fix P0-301 — Calculation writes BLOCKING issue but still forces READY

**Files:**
- Modify: `backend/src/main/java/com/mrp/mrp/service/MrpCalculationService.java`
- Modify: `backend/src/main/java/com/mrp/mrp/service/MrpTaskWorkerService.java`
- Test: `backend/src/test/java/com/mrp/mrp/service/CalculationBlockingIssueTest.java`

**Problem:** After inserting `result.issues`, the code unconditionally calls `updateStatusForce(batchId, "READY")` at line135. Worker also unconditionally sets READY at line153.

**Fix:**
1. After calculation, check if any issues have severity `BLOCKING` or `ERROR`
2. If blocking issues exist: run status → `COMPLETED_WITH_ISSUES`, batch status → `WAITING_DECISION`, sync `issue_count`/`unresolved_count`
3. Only if no blocking issues: proceed to `WAITING_DAILY_DISTRIBUTION`
4. Worker must not override calculation service status decisions
5. Add test: calculation with blocking issue → batch stays `WAITING_DECISION`, not `READY`

- [ ] **Step 1: Write failing test**

```java
@Test
void calculationWithBlockingIssueShouldNotSetReady() {
    // Setup: create batch, add materials with MISSING_CATEGORY_ROLE
    // Execute calculation
    // Assert: batch status = WAITING_DECISION, not READY
    // Assert: run status = COMPLETED_WITH_ISSUES
}
```

- [ ] **Step 2: Modify MrpCalculationService.executeCalculation()**

```java
// After inserting issues:
boolean hasBlocking = result.issues.stream()
    .anyMatch(i -> "BLOCKING".equals(i.getSeverity()) || "ERROR".equals(i.getSeverity()));

if (hasBlocking) {
    run.setStatus("COMPLETED_WITH_ISSUES");
    batchMapper.updateStatusForce(batchId, "WAITING_DECISION");
    batch.setIssueCount(result.issues.size());
    batch.setUnresolvedCount((int) result.issues.stream()
        .filter(i -> "OPEN".equals(i.getStatus())).count());
    batchMapper.update(batch);
} else {
    run.setStatus("COMPLETED");
    batchMapper.updateStatusForce(batchId, "WAITING_DAILY_DISTRIBUTION");
}
```

- [ ] **Step 3: Modify MrpTaskWorkerService to respect calculation status**

```java
// After calculation completes, check run status before distributing
if ("COMPLETED_WITH_ISSUES".equals(run.getStatus())) {
    log.info("Batch {} has blocking issues, skipping distribution", batchId);
    calcTaskMapper.updateStatus(claimedTask.getId(), CalcTask.STATUS_SUCCEEDED, claimedTask.getVersion());
    return true;
}
// Only distribute if no blocking issues
batchMapper.updateStatusForce(batchId, "WAITING_DAILY_DISTRIBUTION");
// ... distribute ...
```

- [ ] **Step 4: Run test to verify**

Run: `./mvnw test -Dtest=CalculationBlockingIssueTest -pl backend`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/mrp/mrp/service/MrpCalculationService.java
git add backend/src/main/java/com/mrp/mrp/service/MrpTaskWorkerService.java
git add backend/src/test/java/com/mrp/mrp/service/CalculationBlockingIssueTest.java
git commit -m "fix: P0-301 - calculation with blocking issues should not set READY"
```

---

### Task A2: Fix P0-302 — Run isolation, all queries use active_run_id

**Files:**
- Modify: `backend/src/main/java/com/mrp/mrp/service/DailyPlanningService.java`
- Modify: `backend/src/main/java/com/mrp/mrp/service/BatchOrchestratorService.java`
- Modify: `backend/src/main/java/com/mrp/mrp/repository/MrpRequirementDetailMapper.java` (add selectByRunId)
- Modify: `backend/src/main/resources/mapper/mrp/MrpRequirementDetailMapper.xml` (add selectByRunId)
- Modify: `backend/src/main/resources/mapper/mrp/MrpTerminalDemandMapper.xml` (add selectByRunId)
- Modify: `backend/src/main/resources/mapper/mrp/MrpDailyPlanDetailMapper.xml` (add selectByRunId)
- Test: `backend/src/test/java/com/mrp/mrp/service/RunIsolationTest.java`

**Problem:** `DailyPlanningService.distribute()` uses `terminalDemandMapper.selectByBatchId` instead of `selectByRunId`. All result queries should resolve `active_run_id` from batch first.

**Fix:**
1. Add `selectByRunId` methods to all result Mappers
2. Modify `DailyPlanningService.distribute()` to accept `runId` parameter (already has it, but uses batchId for queries)
3. All result queries: `requirement_detail`, `terminal_demand`, `daily_plan_detail`, `unallocated_quantity` must use `run_id`
4. Conservation check, weekly/monthly summary must use active run
5. Add test: two runs, verify only active run results returned

- [ ] **Step 1: Add selectByRunId to Mappers**

```xml
<!-- MrpRequirementDetailMapper.xml -->
<select id="selectByRunId" resultType="com.mrp.mrp.domain.MrpRequirementDetail">
    SELECT * FROM mrp_requirement_detail WHERE run_id = #{runId}
</select>
```

- [ ] **Step 2: Modify DailyPlanningService to use runId**

```java
// Change distribute() to use runId for terminal demand query
List<MrpTerminalDemand> terminalDemands = terminalDemandMapper.selectByRunId(runId);
```

- [ ] **Step 3: Modify all getDailyPlans/getWeeklySummary/getMonthlySummary to use active_run_id**

```java
public List<MrpDailyPlanDetail> getDailyPlans(Long batchId) {
    MrpCalcBatch batch = batchMapper.selectById(batchId);
    Long runId = batch != null ? batch.getActiveRunId() : null;
    if (runId == null) return Collections.emptyList();
    return dailyPlanMapper.selectByRunId(runId);
}
```

- [ ] **Step 4: Write and run isolation test**

```java
@Test
void queryShouldOnlyReturnActiveRunResults() {
    // Create batch with run1 (succeeded)
    // Create run2 (succeeded, set as active)
    // Verify: getDailyPlans only returns run2 data
}
```

- [ ] **Step 5: Commit**

```bash
git commit -m "fix: P0-302 - all result queries use active_run_id for isolation"
```

---

## Phase B: Worker Lease/Heartbeat/Retry (P0-303, P0-304, P0-305)

### Task B1: Fix P0-303 — Implement Worker lease/heartbeat/retry

**Files:**
- Create: `backend/src/main/resources/db/migration/V028__add_worker_lease_fields.sql`
- Modify: `backend/src/main/resources/mapper/task/CalcTaskMapper.xml`
- Modify: `backend/src/main/java/com/mrp/task/domain/CalcTask.java`
- Modify: `backend/src/main/java/com/mrp/mrp/service/MrpTaskWorkerService.java`
- Test: `backend/src/test/java/com/mrp/mrp/service/WorkerLeaseTest.java`

**Problem:** `updateStatus` only updates status/version; `failStuckTasks` uses `created_at` for timeout; no `worker_id`, `lease_until`, `started_at`, or `attempt_count` tracking.

**Fix:**
1. V028: Add `worker_id`, `lease_until`, `heartbeat_at`, `started_at`, `attempt_count` columns to `calc_task`
2. Claim: CAS write `worker_id/started_at/heartbeat_at/lease_until/attempt_count`
3. Heartbeat: extend `lease_until` periodically
4. Retry: only `RUNNING` tasks with `lease_until < now` can be retried; PENDING not by `created_at`
5. Max attempts, backoff, final FAILED must be persisted
6. Status update carries `version` + `worker_id` condition

- [ ] **Step 1: Create V028 migration**

```sql
-- V028: Add worker lease fields to calc_task
ALTER TABLE calc_task
    ADD COLUMN worker_id VARCHAR(64) DEFAULT NULL COMMENT 'Worker instance ID' AFTER status,
    ADD COLUMN lease_until TIMESTAMP NULL COMMENT 'Lease expiration time' AFTER worker_id,
    ADD COLUMN heartbeat_at TIMESTAMP NULL COMMENT 'Last heartbeat time' AFTER lease_until,
    ADD COLUMN started_at TIMESTAMP NULL COMMENT 'When task execution started' AFTER heartbeat_at;

CREATE INDEX idx_calc_task_lease ON calc_task(status, lease_until);
```

- [ ] **Step 2: Update CalcTask domain and Mapper**

```xml
<!-- Update claim to include lease fields -->
<update id="claimTask">
    UPDATE calc_task
    SET status = 'RUNNING', worker_id = #{workerId}, started_at = NOW(),
        heartbeat_at = NOW(), lease_until = DATE_ADD(NOW(), INTERVAL 5 MINUTE),
        attempt_count = attempt_count + 1, version = version + 1
    WHERE id = #{id} AND status = 'PENDING' AND version = #{version}
</update>

<update id="heartbeat">
    UPDATE calc_task
    SET heartbeat_at = NOW(), lease_until = DATE_ADD(NOW(), INTERVAL 5 MINUTE)
    WHERE id = #{id} AND worker_id = #{workerId} AND status = 'RUNNING'
</update>

<update id="failStuckTasks">
    UPDATE calc_task
    SET status = 'FAILED', error_code = 'LEASE_EXPIRED',
        error_message = 'Task lease expired without heartbeat',
        finished_at = NOW(), version = version + 1
    WHERE status = 'RUNNING' AND lease_until < NOW() AND attempt_count >= max_attempts
</update>
```

- [ ] **Step 3: Update MrpTaskWorkerService with heartbeat**

```java
@Scheduled(fixedDelay = 30000) // heartbeat every30s
public void heartbeat() {
    // For all RUNNING tasks owned by this worker, extend lease
    calcTaskMapper.heartbeatForWorker(workerId);
}

private String generateWorkerId() {
    return InetAddress.getLocalHost().getHostName() + "-" + ProcessHandle.current().pid();
}
```

- [ ] **Step 4: Write and run lease tests**

- [ ] **Step 5: Commit**

---

### Task B2: Fix P0-304 — Failure cleanup must not delete previous successful run

**Files:**
- Modify: `backend/src/main/java/com/mrp/mrp/service/MrpTaskWorkerService.java`
- Modify: `backend/src/main/resources/mapper/task/CalcTaskMapper.xml`
- Test: `backend/src/test/java/com/mrp/mrp/service/FailureCleanupTest.java`

**Problem:** `cleanupHalfFinishedData()` takes `batch.getActiveRunId()` which may be the OLD successful run, not the current failed run.

**Fix:**
1. When calculation starts, create run and put `runId` in task payload
2. Cleanup only deletes data for the task's own `runId`
3. Never delete `active_run_id` pointing to historical successful run
4. Failed run preserves error and input evidence; active run unchanged
5. Test: "existing active run, new run中途失败" regression test

- [ ] **Step 1: Store runId in task payload when calculation starts**

```java
// In tryClaimAndExecute, after creating run:
Map<String, Object> resultPayload = new LinkedHashMap<>(payload);
resultPayload.put("runId", run.getId());
claimedTask.setInputPayload(objectMapper.writeValueAsString(resultPayload));
calcTaskMapper.updateInputPayload(claimedTask.getId(), claimedTask.getInputPayload());
```

- [ ] **Step 2: Modify cleanupHalfFinishedData to use task's runId**

```java
private void cleanupHalfFinishedData(Long taskId) {
    CalcTask task = calcTaskMapper.selectById(taskId);
    Map<String, Object> payload = objectMapper.readValue(task.getInputPayload(), ...);
    Long runId = toLong(payload.get("runId"));
    Long batchId = toLong(payload.get("batchId"));
    Long activeRunId = batchMapper.selectById(batchId).getActiveRunId();

    // Only cleanup if this runId is NOT the active run
    if (runId != null && !runId.equals(activeRunId)) {
        requirementDetailMapper.deleteByRunId(runId);
        terminalDemandMapper.deleteByRunId(runId);
        dailyPlanMapper.deleteByRunId(runId);
        unallocatedMapper.deleteByRunId(runId);
    }
}
```

- [ ] **Step 3: Write and run regression test**

- [ ] **Step 4: Commit**

---

### Task B3: Fix P0-305 — Task input must use batch-bound snapshots

**Files:**
- Modify: `backend/src/main/java/com/mrp/mrp/controller/MrpBatchLifecycleController.java`
- Modify: `backend/src/main/java/com/mrp/mrp/service/BatchOrchestratorService.java`
- Test: `backend/src/test/java/com/mrp/mrp/service/InputBindingTest.java`

**Problem:** `calculate` API accepts `bomSnapshotId/inventorySnapshotId/rootDemands` from request, which can override batch-bound values.

**Fix:**
1. `calculate` API only accepts `batchId` (remove `bomSnapshotId/inventorySnapshotId` params)
2. Worker loads root demands from batch's `forecastVersionId`
3. Task payload stores all version IDs, root demand checksum, decision checksum
4. Pre/post calculation checksum validation; if changed → STALE/FAILED

- [ ] **Step 1: Modify calculate API signature**

```java
@PostMapping("/{batchId}/calculate")
public ResponseEntity<ApiResponse<CalcTask>> calculate(@PathVariable Long batchId) {
    CalcTask task = orchestrator.submitCalculation(batchId, CurrentUser.getUserId());
    return ResponseEntity.ok(ApiResponse.ok(task));
}
```

- [ ] **Step 2: Modify submitCalculation to load from batch**

```java
public CalcTask submitCalculation(Long batchId, Long operatorId) {
    MrpCalcBatch batch = batchMapper.selectById(batchId);
    // Load root demands from batch's forecast version
    List<NetRequirementEngine.RootDemand> rootDemands = loadRootDemandsFromForecast(batch.getForecastVersionId());
    // Use batch-bound snapshot IDs
    Long bomSnapshotId = batch.getBomSnapshotId();
    Long inventorySnapshotId = batch.getInventorySnapshotId();
    // ... build payload with all version IDs ...
}
```

- [ ] **Step 3: Add checksum validation**

```java
// Before calculation:
String currentInputChecksum = computeInputChecksum(batch);
if (batch.getInputChecksum() != null && !batch.getInputChecksum().equals(currentInputChecksum)) {
    batchMapper.updateStatusForce(batchId, "STALE");
    throw new IllegalStateException("Input changed since precheck, batch marked STALE");
}
```

- [ ] **Step 4: Write and run test**

- [ ] **Step 5: Commit**

---

## Phase C: Inventory & BOM (P0-306, P0-309)

### Task C1: Fix P0-306 — Inventory missing detection with dimensions

**Files:**
- Modify: `backend/src/main/java/com/mrp/mrp/engine/NetRequirementEngine.java`
- Modify: `backend/src/main/java/com/mrp/mrp/service/MrpCalculationService.java`
- Test: `backend/src/test/java/com/mrp/mrp/engine/InventoryDimensionTest.java`

**Problem:** Inventory map only uses `materialCode` as key, not `factory+org+material+quality`. Empty snapshot → `materialsWithInventory` empty → `getOrDefault(..., ZERO)` silently uses 0.

**Fix:**
1. Inventory key must include `factory_code + inventory_org + material_code + inventory_quality`
2. `materialsWithInventory` must always be populated (even from empty snapshot)
3. For root and every accessed node, missing balance record → `INVENTORY_MISSING` issue
4. `lockedAllocationMap` key must include `demandKey/pathHash`, not just `root/material/month`
5. Add tests: empty inventory, zero inventory, DEFECTIVE, cross-org, shared inventory competition

- [ ] **Step 1: Change inventory key to include dimensions**

```java
// In MrpCalculationService:
Map<String, BigDecimal> inventoryMap = new LinkedHashMap<>();
for (MrpInventoryBalance balance : goodBalances) {
    String key = balance.getFactoryCode() + "|" + balance.getInventoryOrg() + "|"
        + balance.getMaterialCode() + "|" + balance.getInventoryQuality();
    inventoryMap.merge(key, balance.getAvailableQuantity(), BigDecimal::add);
}
```

- [ ] **Step 2: Always populate materialsWithInventory**

```java
// Always build the set, even if empty
Set<String> materialsWithInventory = allBalances.stream()
    .map(b -> b.getFactoryCode() + "|" + b.getInventoryOrg() + "|" + b.getMaterialCode())
    .collect(Collectors.toSet());
```

- [ ] **Step 3: Update engine to use dimension-aware keys**

```java
private BigDecimal getAvailableInventory(String materialCode, Map<String, BigDecimal> usedInventory) {
    String key = factoryCode + "|" + inventoryOrg + "|" + materialCode + "|GOOD";
    BigDecimal total = inventoryMap.getOrDefault(key, BigDecimal.ZERO);
    BigDecimal used = usedInventory.getOrDefault(key, BigDecimal.ZERO);
    return total.subtract(used).max(BigDecimal.ZERO);
}
```

- [ ] **Step 4: Write and run dimension tests**

- [ ] **Step 5: Commit**

---

### Task C2: Fix P0-309 — Improve BOM snapshot adjudication

**Files:**
- Modify: `backend/src/main/java/com/mrp/mrp/service/BomSnapshotService.java`
- Test: `backend/src/test/java/com/mrp/mrp/service/BomSnapshotAdjudicationTest.java`

**Problem:** Current code only checks `effective_date <= asOf`, doesn't check失效日期区间; can't detect same version duplicate rows or real有效期重叠.

**Fix:**
1. NC sync groups by `inventory_org + parent + child`, in as-of有效集合按规范版本值取最高
2. Same key multiple最高版本、相同版本重复、有效区间重叠、组织为空、数量非法全部阻断
3. MRP only reads batch-bound and solidified READY snapshot
4. Checksum stable includes org, parent/child, qty, version, effective/失效 dates
5. Add tests: org isolation, version sort, duplicate最高版本, effective overlap, snapshot immutability

- [ ] **Step 1: Enhance loadEdgesFromSource with失效 date check**

```java
// Add失效 date to query
"SELECT d.parent_code, d.child_code, d.parent_qty, d.child_qty, d.version as bom_version, " +
"d.effective_date, d.expiry_date, d.org_name as organization_code, d.body_code as body_scope " +
"FROM bom_detail d " +
"WHERE d.parent_qty > 0 AND d.child_qty > 0 " +
"AND (d.is_default = 'Y' OR d.is_default IS NULL) " +
"AND (d.effective_date IS NULL OR d.effective_date <= ?) " +
"AND (d.expiry_date IS NULL OR d.expiry_date >= ?)"
```

- [ ] **Step 2: Add duplicate version detection**

```java
// After finding highest version rows:
long distinctVersions = highestVersionRows.stream()
    .map(BomSnapshotDetail::getBomVersion)
    .distinct().count();
if (distinctVersions > 1) {
    errors.add("MULTIPLE_ACTIVE_BOM: ...");
    continue;
}
```

- [ ] **Step 3: Add effective date overlap detection**

```java
// Check for overlapping effective ranges within same version
List<LocalDate> effectiveDates = highestVersionRows.stream()
    .map(BomSnapshotDetail::getEffectiveDate)
    .filter(Objects::nonNull)
    .sorted().collect(Collectors.toList());
if (effectiveDates.size() > 1) {
    errors.add("BOM_EFFECTIVE_OVERLAP: ...");
}
```

- [ ] **Step 4: Write and run adjudication tests**

- [ ] **Step 5: Commit**

---

## Phase D: Execution & Publish (P0-307, P0-308)

### Task D1: Fix P0-307 — Execution confirmation with real rowKey and quantity validation

**Files:**
- Modify: `backend/src/main/java/com/mrp/mrp/service/MrpExecutionService.java`
- Modify: `backend/src/main/java/com/mrp/mrp/repository/MrpDailyPlanDetailMapper.java`
- Modify: `backend/src/main/resources/mapper/mrp/MrpDailyPlanDetailMapper.xml`
- Test: `backend/src/test/java/com/mrp/mrp/service/ExecutionConfirmationTest.java`

**Problem:** `confirmExecution()` builds `rowKey` as `terminalMaterialCode + "|" + scheduleDate` but daily plan uses SHA-256 key. No validation that rowKey belongs to batch's active run. No quantity difference check.

**Fix:**
1. `confirmExecution` accepts real `rowKey` from daily plan detail
2. Server validates rowKey belongs to batch's active run and scheduleDate
3. Lock saves plan effective quantity; confirm must fill actual qty, operator, remark
4. Actual qty != locked plan qty → persist `EXECUTION_QUANTITY_MISMATCH` exception
5. LOCKED/EXECUTED dates: no override, recalc, revision overwrite
6. Tests: non-existent rowKey, cross-batch rowKey, quantity mismatch, duplicate submit

- [ ] **Step 1: Add selectByRowKey to MrpDailyPlanDetailMapper**

```xml
<select id="selectByRowKey" resultType="com.mrp.mrp.domain.MrpDailyPlanDetail">
    SELECT * FROM mrp_daily_plan_detail WHERE batch_id = #{batchId} AND row_key = #{rowKey} AND run_id = #{runId}
</select>
```

- [ ] **Step 2: Rewrite confirmExecution with rowKey validation**

```java
public MrpDailyExecution confirmExecution(Long batchId, String rowKey, BigDecimal actualQty,
                                           Long operatorId, String remark) {
    MrpCalcBatch batch = batchMapper.selectById(batchId);
    MrpDailyPlanDetail plan = dailyPlanMapper.selectByRowKey(batchId, rowKey, batch.getActiveRunId());
    if (plan == null) {
        throw new IllegalArgumentException("RowKey not found in active run: " + rowKey);
    }

    // Check LOCKED/EXECUTED
    MrpDailyExecution existing = executionMapper.selectByBatchRowDate(batchId, rowKey, plan.getScheduleDate());
    if (existing != null && ("LOCKED".equals(existing.getExecutionStatus()) || "EXECUTED".equals(existing.getExecutionStatus()))) {
        throw new IllegalStateException("Cannot confirm: " + existing.getExecutionStatus());
    }

    // Create execution with quantity difference check
    MrpDailyExecution execution = new MrpDailyExecution();
    execution.setBatchId(batchId);
    execution.setRowKey(rowKey);
    execution.setScheduleDate(plan.getScheduleDate());
    execution.setExecutionStatus("EXECUTED");
    execution.setActualQuantity(actualQty);
    execution.setPlannedQuantity(plan.getSystemQuantity());
    execution.setConfirmedBy(operatorId);
    execution.setConfirmedAt(LocalDateTime.now());
    execution.setRemark(remark);

    // Check quantity mismatch
    if (actualQty.compareTo(plan.getSystemQuantity()) != 0) {
        MrpCalcException mismatch = new MrpCalcException();
        mismatch.setRunId(batch.getActiveRunId());
        mismatch.setBatchId(batchId);
        mismatch.setSeverity("WARNING");
        mismatch.setErrorCode("EXECUTION_QUANTITY_MISMATCH");
        mismatch.setScopeKey(rowKey);
        mismatch.setMessage("Actual qty " + actualQty + " != planned qty " + plan.getSystemQuantity());
        exceptionMapper.insert(mismatch);
    }

    executionMapper.insert(execution);
    return execution;
}
```

- [ ] **Step 3: Add lockDay with planned quantity snapshot**

- [ ] **Step 4: Write and run tests**

- [ ] **Step 5: Commit**

---

### Task D2: Fix P0-308 — Publish/revise create independent run/facts

**Files:**
- Modify: `backend/src/main/java/com/mrp/mrp/service/BatchOrchestratorService.java`
- Test: `backend/src/test/java/com/mrp/mrp/service/PublishReviseTest.java`

**Problem:** `publish()` sets `revision.setActiveRunId(batch.getActiveRunId())` — sharing run identity. `revise()` copies plans with `runId=null`. Neither copies issues, decisions, terminal demands, or requirement details.

**Fix:**
1. Publish only marks batch as PUBLISHED, creates new version batch with independent run
2. Revise creates independent batch, independent run, independent daily facts
3. Only copy executed/locked days as read-only facts; future unlocked days recalculate
4. Provide explicit revision API and inventory allocation choice
5. Tests: publish, revise, locked day protection, failure rollback, historical run isolation

- [ ] **Step 1: Rewrite publish to create independent version**

```java
public MrpCalcBatch publish(Long batchId) {
    // ... validation ...

    // Create published version batch (copy)
    MrpCalcBatch published = new MrpCalcBatch();
    // ... copy all fields ...
    published.setStatus("PUBLISHED");
    published.setPublishedAt(LocalDateTime.now());
    published.setRevisionOfBatchId(batchId);
    published.setRevisionNo(batch.getRevisionNo() + 1);
    batchMapper.insert(published);

    // Create independent run for published version
    MrpCalcRun publishedRun = new MrpCalcRun();
    publishedRun.setBatchId(published.getId());
    publishedRun.setStatus("PUBLISHED");
    publishedRun.setStartedAt(LocalDateTime.now());
    publishedRun.setFinishedAt(LocalDateTime.now());
    runMapper.insert(publishedRun);
    published.setActiveRunId(publishedRun.getId());
    batchMapper.update(published);

    // Copy daily plans with new runId
    List<MrpDailyPlanDetail> sourcePlans = dailyPlanMapper.selectByRunId(batch.getActiveRunId());
    for (MrpDailyPlanDetail plan : sourcePlans) {
        plan.setRunId(publishedRun.getId());
        plan.setBatchId(published.getId());
    }
    dailyPlanMapper.insertBatch(sourcePlans);

    // Mark original as superseded
    batch.setSupersededByBatchId(published.getId());
    batchMapper.update(batch);

    return published;
}
```

- [ ] **Step 2: Rewrite revise to preserve locked/executed days**

```java
public MrpCalcBatch revise(Long batchId) {
    // ... validation ...

    // Create revision batch
    MrpCalcBatch revision = new MrpCalcBatch();
    // ... copy fields ...
    revision.setStatus("DRAFT");
    revision.setRevisionOfBatchId(batchId);
    revision.setRevisionNo(batch.getRevisionNo() + 1);
    batchMapper.insert(revision);

    // Copy only LOCKED/EXECUTED daily plans as read-only facts
    List<MrpDailyPlanDetail> sourcePlans = dailyPlanMapper.selectByRunId(batch.getActiveRunId());
    List<MrpDailyPlanDetail> lockedPlans = sourcePlans.stream()
        .filter(p -> isDateLockedOrExecuted(batchId, p.getScheduleDate()))
        .collect(Collectors.toList());
    for (MrpDailyPlanDetail plan : lockedPlans) {
        plan.setBatchId(revision.getId());
        // runId will be set when new calculation runs
    }
    if (!lockedPlans.isEmpty()) {
        dailyPlanMapper.insertBatch(lockedPlans);
    }

    return revision;
}
```

- [ ] **Step 3: Write and run tests**

- [ ] **Step 4: Commit**

---

## Phase E: Permissions & Frontend (P1-301, P1-302, P1-303)

### Task E1: Fix P1-301 — Complete MRP fine-grained permissions

**Files:**
- Modify: `backend/src/main/java/com/mrp/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/mrp/mrp/controller/MrpConfigController.java`
- Modify: `backend/src/main/java/com/mrp/mrp/controller/MrpExecutionController.java`
- Test: `backend/src/test/java/com/mrp/security/MrpPermissionTest.java`

**Problem:** `MrpConfigController` uses hardcoded `1L`; inventory allocation allows request `operatorId`; missing fine-grained permissions for config, inventory, execution, calendar.

**Fix:**
1. Add permissions: `mrp_batch:view/create/precheck/calculate/publish`, `mrp_decision:manage`, `mrp_inventory:allocate`, `mrp_execution:confirm`, `mrp_calendar:manage`, `mrp_plan:adjust/export`
2. Replace all hardcoded `1L` with `CurrentUser.getUserId()`
3. Validate factory data domain: 403 for cross-factory access
4. Add real curl/API tests for401/403

- [ ] **Step 1: Add missing permissions to SecurityConfig**

```java
// MRP config permissions
.requestMatchers(HttpMethod.POST, "/api/v1/mrp/category-role-versions/**").hasAuthority("mrp_config:manage")
.requestMatchers(HttpMethod.POST, "/api/v1/mrp/material-policy-versions/**").hasAuthority("mrp_config:manage")
.requestMatchers(HttpMethod.POST, "/api/v1/production-calendar-versions/**").hasAuthority("mrp_calendar:manage")

// MRP execution permissions
.requestMatchers(HttpMethod.POST, "/api/v1/mrp/execution/**").hasAuthority("mrp_execution:confirm")
.requestMatchers(HttpMethod.GET, "/api/v1/mrp/execution/**").hasAuthority("mrp_batch:list")

// MRP inventory allocation permissions
.requestMatchers(HttpMethod.PUT, "/api/v1/mrp/batches/*/inventory-pools/**").hasAuthority("mrp_inventory:allocate")
.requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/inventory-pools/**").hasAuthority("mrp_inventory:allocate")
```

- [ ] **Step 2: Replace hardcoded operator IDs**

- [ ] **Step 3: Write and run permission tests**

- [ ] **Step 4: Commit**

---

### Task E2: Fix P1-302 — Build Phase2 frontend workspace pages

**Files:**
- Modify: `frontend/src/views/mrp/MrpBatchDetail.vue`
- Create: `frontend/src/views/mrp/DailyGrid.vue`
- Create: `frontend/src/views/mrp/DecisionCenter.vue`
- Create: `frontend/src/views/mrp/InventoryAllocation.vue`
- Create: `frontend/src/views/mrp/ExecutionConfirm.vue`
- Modify: `frontend/src/api/mrp.ts`

**Problem:** Only batch list and detail pages exist. Missing daily grid, decision center, inventory allocation, execution confirm, weekly/monthly summary. Detail shows "来源日/排产日" dual timeline. TypeScript uses `number` instead of decimal strings.

**Fix:**
1. Create daily grid page with fixed columns: 来源整机, 末级需求物料, 终点/供应, 工厂
2. Create decision center page with issue list, resolve actions
3. Create inventory allocation page with pool, demands, allocation
4. Create execution confirm page
5. Fix all decimal quantities to string type
6. Follow prototype layout, use Element Plus, handle empty/loading/error states

- [ ] **Step 1: Create DailyGrid.vue**

```vue
<template>
  <div class="daily-grid">
    <div class="page-header">
      <h1 class="page-title">日排产</h1>
      <div class="page-actions">
        <el-button type="primary" @click="handleExport">导出</el-button>
      </div>
    </div>
    <div class="filter-bar">
      <el-input v-model="searchRoot" placeholder="来源整机" style="width: 220px" />
      <el-input v-model="searchTerminal" placeholder="末级物料" style="width: 220px" />
      <el-date-picker v-model="dateRange" type="daterange" />
    </div>
    <el-table v-loading="loading" :data="plans" border>
      <el-table-column prop="rootMaterialCode" label="来源整机" fixed width="150" />
      <el-table-column prop="terminalMaterialCode" label="末级需求物料" fixed width="150" />
      <el-table-column prop="terminalType" label="终点类型" width="100" />
      <el-table-column prop="supplyMode" label="供应方式" width="100" />
      <el-table-column prop="factoryCode" label="工厂" width="80" />
      <el-table-column v-for="day in calendarDays" :key="day" :label="day" width="80">
        <template #default="{ row }">
          <span @click="openDrawer(row, day)">{{ getQty(row, day) }}</span>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination layout="total, sizes, prev, pager, next" :total="total" />
  </div>
</template>
```

- [ ] **Step 2: Create DecisionCenter.vue**

- [ ] **Step 3: Create InventoryAllocation.vue**

- [ ] **Step 4: Create ExecutionConfirm.vue**

- [ ] **Step 5: Fix decimal types in mrp.ts**

- [ ] **Step 6: Build and test frontend**

- [ ] **Step 7: Commit**

---

### Task E3: Fix P1-303 — Create Phase2 Playwright/UAT tests

**Files:**
- Create: `frontend/tests/e2e/mrp-lifecycle.spec.ts`
- Modify: `frontend/playwright.config.ts` (if needed)

**Problem:** Existing E2E covers Phase1 `/planning`; no batch creation, precheck, decision, inventory allocation, async task, daily grid, execution confirm, revision, publish blocking.

**Fix:**
1. Create Playwright test for full Phase2 lifecycle:
   - Login → MRP batches → Create batch → Precheck → Resolve issues → Calculate → Daily grid → Publish → Revise
2. Assert each step's status and decimal quantities
3. Verify blocked states: STALE, waiting decision, waiting allocation
4. Verify401/403 handling

- [ ] **Step 1: Create mrp-lifecycle.spec.ts**

```typescript
test('MRP full lifecycle', async ({ page }) => {
  // Login
  await page.goto('/login');
  await page.fill('[placeholder="邮箱"]', 'admin');
  await page.fill('[placeholder="密码"]', 'admin123');
  await page.click('button:has-text("登录")');

  // Navigate to MRP batches
  await page.goto('/mrp/batches');
  await page.waitForSelector('text=MRP 计算批次');

  // Create batch
  await page.click('button:has-text("新增批次")');
  // ... fill form ...

  // Precheck
  await page.click('button:has-text("预检")');
  await page.waitForSelector('text=READY_TO_CALCULATE');

  // Calculate
  await page.click('button:has-text("计算")');
  await page.waitForSelector('text=READY', { timeout: 30000 });

  // Verify daily grid
  await page.click('text=日排产');
  await page.waitForSelector('.daily-grid');

  // Verify quantities are decimal strings
  const cells = await page.locator('.daily-grid td').all();
  // ... assert ...
});
```

- [ ] **Step 2: Run Playwright tests**

Run: `cd frontend && npx playwright test tests/e2e/mrp-lifecycle.spec.ts`

- [ ] **Step 3: Commit**

---

## Phase F: Final Verification

### Task F1: Full verification and report

**Files:**
- Modify: `.harness/workflow.yaml`
- Modify: `.harness/issue-log.md`

- [ ] **Step 1: Run backend full test suite**

```bash
cd backend && ./mvnw clean compile && ./mvnw test
```

- [ ] **Step 2: Run frontend checks**

```bash
cd frontend && npm run build && npm test -- --run && npx vue-tsc --noEmit
```

- [ ] **Step 3: Start server and run API verification**

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# Run curl tests for full lifecycle
```

- [ ] **Step 4: Browser verification**

- [ ] **Step 5: Update workflow.yaml and issue-log**

- [ ] **Step 6: Final commit**

---

## Reviewer Checklist

Before marking any task DONE, verify:
1. Code compiles without errors
2. Unit tests pass
3. No `float`/`double`/`long` for quantities
4. No hardcoded `1L` for operator
5. No unconditional `READY` after blocking issues
6. All result queries use `run_id`, not `batch_id`
7. Worker has proper lease/heartbeat
8. Failure cleanup doesn't delete successful run
9. Input comes from batch-bound snapshots
10. Inventory key includes factory/org/material/quality
11. Execution uses real SHA-256 rowKey
12. Publish/revise creates independent run/facts
13. LOCKED/EXECUTED days are protected
14. All permissions are fine-grained
15. Frontend handles empty/loading/error states
