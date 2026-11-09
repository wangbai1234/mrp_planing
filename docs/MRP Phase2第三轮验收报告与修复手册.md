# MRP Phase 2 第三轮验收报告与修复手册

**验收日期**：2026-09-23  
**验收对象**：Mimo 最新工作区  
**结论**：`FAIL / 暂不签收 Phase 2`

## 1. 结论摘要

本轮确认 Mimo 已补充了 V026/V027、计算 Worker、GOOD/DEFECTIVE 库存字段、日计划 `row_key`、执行确认接口、周/月汇总骨架、结果 issue 持久化和 BOM 版本测试。但这些改动仍没有形成可发布的 Phase 2 闭环，不能把阶段状态改为 `DONE`，也不能把历史 UAT 报告中的 PASS 作为当前证据。

阻断签收的原因是：计算即使产生 BLOCKING issue 仍可能进入 `READY`，不同计算 run 会按 `batch_id` 混读，Worker 没有真正的租约与心跳，执行记录无法绑定真实日计划行，发布/修订会复制或删除错误的 run，权限与前端工作台也不完整。

## 2. 本轮真实验证证据

| 验证项 | 结果 | 证据 |
|---|---|---|
| 后端编译 | PASS | `backend/./mvnw compile -q` |
| 后端 clean 编译 | 未通过 | `./mvnw clean compile -q` 曾因 `backend/target/classes` 文件被占用失败；随后串行 `compile` 通过，不能把 clean 结果写成 PASS |
| 后端全量测试 | FAIL | `146` 个测试，`BomExplosionServiceTest` 因 Testcontainers 找不到有效 Docker 环境错误；不可用排除测试的命令冒充 PASS |
| BOM 集成测试单独执行 | FAIL/环境阻断 | `./mvnw -q -Dtest=BomExplosionServiceTest test`，Docker socket 返回无效 daemon 信息 |
| 前端单元测试 | PASS | `npm test -- --run`，2 个文件、15 个测试 |
| 前端构建 | PASS | `npm run build` |
| 差异检查 | PASS | `git diff --check` |
| Flyway | PASS（已有环境） | Spring 测试日志校验到 V027 |
| 后端真实 API | 未通过 | 当前没有可用 MRP API 服务；对 10484 连接被 reset，8080 无服务 |
| 浏览器联调 | 未通过 | Vite 页面可返回 HTML，但 Cua 浏览器连接因当前认证方式不可用，不能形成业务交互证据 |

Docker/Testcontainers 属于当前环境阻断，不能由本地代理擅自安装或切换版本；修复代码后必须在 Docker 可用的 CI/UAT 环境重新跑全量测试。

## 3. 已确认的有效改动

以下内容确实已经存在，本报告不重复要求“重新搭骨架”：

- `V026` 增加任务索引、执行表字段，`V027` 增加日计划 `row_key`；
- 库存导入/规范化已出现 `GOOD`、`DEFECTIVE` 和 DECIMAL 数量；
- `NetRequirementEngine` 已有 STOP/EXPLODE、循环检查、BigDecimal 计算和 issue 返回；
- 预检已尝试从经营计划版本读取 root demand；
- Worker、日分配、执行锁定/解锁、周/月汇总接口和 SHA-256 row key 已出现；
- `SecurityConfig` 已增加部分 MRP 权限和 401/403 handler；
- 前端已能构建，已有批次列表和详情页。

## 4. P0 阻断问题

### P0-301：计算写入 BLOCKING issue 后仍强制 READY

**证据**：[MrpCalculationService.java](../backend/src/main/java/com/mrp/mrp/service/MrpCalculationService.java:114) 插入 `result.issues` 后，在第 135 行无条件 `updateStatusForce(batchId, "READY")`。Worker 在日分配后也在第 153 行无条件设置 `READY`。

**影响**：缺库存、缺分类角色、缺物料策略、BOM 循环、EXPLODE 无 BOM 等阻断问题会被写入但仍可发布。

**修复要求**：

1. 计算完成后按 issue severity 汇总，存在 `BLOCKING` 或 `ERROR` 时，run 为 `COMPLETED_WITH_ISSUES`（或 `FAILED`），batch 为 `WAITING_DECISION`，同步 `issue_count/unresolved_count`；
2. 无阻断 issue 才允许进入 `WAITING_DAILY_DISTRIBUTION`；日分配完成且无待分配后才进入 `READY`；
3. Worker 不得覆盖计算服务决定的状态；发布前再次以 active run 校验 issue、待分配和守恒；
4. 增加缺库存、循环、缺策略和阻断发布测试。

### P0-302：计算 run 没有隔离，查询普遍按 batch_id 混读

**证据**：[DailyPlanningService.java](../backend/src/main/java/com/mrp/mrp/service/DailyPlanningService.java:52) 用 `terminalDemandMapper.selectByBatchId`，需求、末级需求、日计划、汇总和守恒检查同样大量使用 `selectByBatchId`；只有部分 Mapper 提供 `selectByRunId`。

**影响**：月中重算或失败重算后，历史 run 与当前 run 可能合并，结果、日计划和汇总不再可追溯。

**修复要求**：

1. 批次所有结果查询默认解析 `active_run_id`，底层 SQL 必须带 `run_id`；历史查询必须显式传 runId；
2. 日分配、周/月汇总、守恒检查、导出、详情 API 只读当前 active run；
3. 为每次 run 增加独立 checksum、状态和输入快照，禁止新 run 复用旧 run 身份；
4. 增加“两次重算、一次失败、查询只返回 active run”的集成测试。

### P0-303：Worker 没有真正的 lease/heartbeat/retry

**证据**：[CalcTaskMapper.xml](../backend/src/main/resources/mapper/task/CalcTaskMapper.xml:25) 的 `updateStatus` 只更新 status/version；`failStuckTasks` 按 `created_at` 判断超时；[MrpTaskWorkerService.java](../backend/src/main/java/com/mrp/mrp/service/MrpTaskWorkerService.java:96) 没有写入 worker_id、lease_until、started_at 或 attempt count。

**影响**：排队超过超时阈值会被误判失败；Worker 崩溃后任务不能可靠接管；多 Worker 不能证明租约互斥。

**修复要求**：

1. 新增 V028+（不能修改 V001-V027）或确认已有字段后补 SQL：claim 时 CAS 写 `worker_id/started_at/heartbeat_at/lease_until/attempt_count`；
2. heartbeat 延长租约，只有 `lease_until < now` 的 RUNNING 任务可重试；PENDING 不按 created_at 超时；
3. 最大重试次数、退避时间和最终 FAILED 必须落库；状态更新携带 version/worker_id 条件；
4. 增加双 Worker 认领、心跳续租、租约过期接管、重复请求和幂等测试。

### P0-304：失败清理可能删除上一次成功的 active run

**证据**：[MrpTaskWorkerService.java](../backend/src/main/java/com/mrp/mrp/service/MrpTaskWorkerService.java:193) 失败时取 `batch.getActiveRunId()` 清理，而当前失败 runId 没有放入任务 payload。

**影响**：新 run 失败时可能删除旧的成功 run，造成已发布结果丢失。

**修复要求**：

1. 计算开始即创建 run，并把 `runId` 写入 task payload/任务上下文；
2. 清理只允许删除该 task 的 runId，且不得删除 `active_run_id` 指向的历史成功 run；
3. 失败时保留 FAILED run 的错误和输入证据，active run 不变；
4. 增加“已有 active run，新 run 中途失败”的回归测试。

### P0-305：任务输入仍可被请求参数替换，未强制使用批次绑定快照

**证据**：[MrpBatchLifecycleController.java](../backend/src/main/java/com/mrp/mrp/controller/MrpBatchLifecycleController.java:43) 接收请求传入 `bomSnapshotId/inventorySnapshotId/rootDemands`；[BatchOrchestratorService.java](../backend/src/main/java/com/mrp/mrp/service/BatchOrchestratorService.java:239) 直接把这些值写入 payload。

**影响**：用户可以让批次绑定的经营计划、BOM、库存与实际计算输入不一致，checksum 也无法代表真实输入。

**修复要求**：

1. calculate API 只接收 batchId/requestKey；Worker 从 batch 固化的版本和经营计划加载 root demand；
2. 请求中的 snapshot/root demand 若保留，只能用于一致性校验，不能作为覆盖值；
3. task payload 固化全部版本 ID、root demand checksum、decision checksum 和规则版本；
4. 计算前后校验快照 checksum 未变化，变化则 STALE/FAILED，不写 READY。

### P0-306：库存缺记录在空快照时仍可能静默按 0

**证据**：[NetRequirementEngine.java](../backend/src/main/java/com/mrp/mrp/engine/NetRequirementEngine.java:137) 只有 `materialsWithInventory` 非空时才检查缺记录；为空时 `getOrDefault(..., ZERO)` 在第 200 行静默按 0。库存计算还只按 materialCode 汇总，未带工厂/库存组织/质量维度。

**影响**：整个库存快照为空、或物料没有记录时无法区分“真实零库存”和“未导入/未确认”。

**修复要求**：

1. 对 root 和每个被访问节点，缺少 balance 记录一律生成 `INVENTORY_MISSING`；业务确认后才可按 0；
2. GOOD 进入可用量，DEFECTIVE 仅保留追溯，永不扣减；查询接口不能过滤掉 DEFECTIVE；
3. 计算 key 至少包含 factory、inventory_org、material、quality；跨组织/共享库存必须使用已锁定 allocation；
4. `lockedAllocationMap` 不能只用 root/material/month，必须包含 demandKey/pathHash；
5. 增加空库存、零库存、DEFECTIVE、跨组织和共享库存竞争测试。

### P0-307：执行确认没有绑定真实日计划 rowKey，也没有数量差异校验

**证据**：[MrpExecutionService.java](../backend/src/main/java/com/mrp/mrp/service/MrpExecutionService.java:31) 自行拼接 `terminalMaterialCode + "|" + scheduleDate`；日计划生成的是包含工厂、根物料、末级物料、终点类型、供应方式、路径、来源月和日期的 SHA-256 key。

**影响**：不同整机/路径/来源月的行会冲突；接口可以为不存在的日计划创建 EXECUTED 记录；实际量与锁定计划不一致不会生成 `EXECUTION_QUANTITY_MISMATCH`。

**修复要求**：

1. confirm API 接收真实 `rowKey`（或 dailyPlanId），服务端校验它属于 batch 的 active run 和 scheduleDate；
2. 锁定时保存计划有效量；执行确认必须填写实际数量、确认人、备注；
3. 实际量与锁定时有效量不等时，持久化差异异常；已 EXECUTED 记录只能按审计规则修订；
4. LOCKED/EXECUTED 日期禁止 override、重算和修订覆盖；增加不存在 rowKey、跨批次 rowKey、数量差异和重复提交测试。

### P0-308：发布和修订共享/复制错误的 run 与日事实

**证据**：[BatchOrchestratorService.java](../backend/src/main/java/com/mrp/mrp/service/BatchOrchestratorService.java:340) 发布时把旧 `activeRunId` 直接写给新批次并复制日计划；修订路径第 450 行将 `runId` 设为 null 后仍插入日计划。

**影响**：旧发布结果与修订结果共享身份；数据库 run 归属可能为空；修订没有复制需求、末级需求、issue、决策和执行关联，也没有只重算未来未锁定日的闭环。

**修复要求**：

1. 发布只发布当前 batch/run，不创建一个共享旧 run 的伪版本；
2. 修订创建独立 batch、独立 run、独立日事实、issue、decision checksum 和 result checksum；
3. 只复制/保留已执行或锁定日为只读事实，未来未锁定日重新计算；
4. 提供显式 revision API 和库存分配口径选择“保留原分配/重新分配”；未选择时禁止发布；
5. 增加发布、修订、锁定日保护、失败回滚和历史 run 隔离测试。

### P0-309：BOM 快照裁决仍不能证明“最高有效版本”唯一

**证据**：[BomSnapshotService.java](../backend/src/main/java/com/mrp/mrp/service/BomSnapshotService.java:65) 只过滤 `effective_date <= asOf`，没有失效日期区间；相同最高版本只以不同 effective date 粗略判断 overlap，无法发现相同版本重复行或真实有效期重叠。

**修复要求**：

1. NC 同步按 `inventory_org + parent + child` 分组，在 as-of 有效集合中按规范版本值取最高；
2. 同一键多个最高版本、相同版本重复、有效区间重叠、组织为空、数量非法全部阻断；
3. MRP 只读取批次绑定且已固化的 READY snapshot，不接受任意请求 snapshotId；
4. checksum 稳定包含组织、父子、数量、版本、生效/失效日期；
5. 增加组织隔离、版本排序、重复最高版本、有效期重叠和 snapshot 不可变测试。

## 5. P1 问题

### P1-301：权限和操作者身份仍不完整

- `MrpConfigController` 创建版本时固定传 `1L`；库存分配更新接口允许请求传 `operatorId`；配置、库存、执行、日历等 POST 没有独立细粒度权限。
- 需要区分 `mrp_batch:view/create/precheck/calculate/publish`、`mrp_decision:manage`、`mrp_inventory:allocate`、`mrp_execution:confirm`、`mrp_calendar:manage`、`mrp_plan:adjust/export`，并执行工厂数据域校验。
- 未认证必须 401，有效身份但无权限/跨工厂必须 403；补真实 curl/API 测试。

### P1-302：前端不是 Phase 2 工作台

当前只有 `MrpBatchList.vue` 和 `MrpBatchDetail.vue`。缺少日排产主网格、来源整机与末级物料固定列、周/月只读汇总、决策中心、库存分配、生产日历、执行确认、修订/版本对比。详情仍展示“来源日/排产日”双时间轴，容易与最终日事实混淆。`frontend/src/api/mrp.ts` 的正式数量仍大量使用 TypeScript `number`，应改为 DECIMAL 字符串。

前端修复必须先读取 `prototype/`、`assets/import-design-rules.md`、`.harness/list-rules.md`，使用 Element Plus，补齐空、加载、错误、分页和真实 API 联调；不得用前端自行聚合代替服务端汇总。

### P1-303：Playwright 与 UAT 不是 Phase 2 验收证据

现有 E2E 主要覆盖 Phase 1 `/planning`；没有批次创建、预检、决策、库存分配、异步任务、日网格、执行确认、修订和发布阻断。历史 `19-docker-uat-final-audit-report.md` 仍写 V001-V021、排除 BOM 测试后 PASS，与当前 V027 和真实失败不一致。

保留历史报告，新增修复后证据；不得改历史文案制造 PASS。需要独立空库迁移、V020 副本升级、`flyway validate`、schema diff 和完整 Phase 2 UAT 日志。

## 6. Mimo 修复顺序

1. 先修 P0-301、P0-302，建立 issue 阻断和 active run 单一真源；
2. 修 P0-303、P0-304、P0-305，完成任务 lease、失败清理和输入绑定；
3. 修 P0-306、P0-309，完成库存维度、缺记录决策和 BOM snapshot 裁决；
4. 修 P0-307、P0-308，完成执行、锁定、月中重算和修订版本；
5. 修 P1 权限、前端工作台、Playwright/UAT 与报告；
6. 每组实现后由独立 Reviewer 复核，不能由实现 Agent 自评置 `DONE`。

## 7. 通过标准

- Docker 可用环境中 `./mvnw clean compile`、`./mvnw test` 全量通过，不能排除测试；
- 前端 `npm test -- --run`、`npm run build`、`vue-tsc --noEmit` 通过；
- 黄金示例逐层扣减、STOP/EXPLODE、缺库存决策、共享库存、BOM 版本阻断和日分配守恒有自动化测试；
- 两次 run 重算只返回 active run，失败不破坏旧 active run；
- LOCKED/EXECUTED 日期不可被重算/override/revision 覆盖，数量差异可追溯；
- 未认证 401、无权限 403、工厂域隔离、幂等和并发认领有真实 API 证据；
- Phase 2 页面和 Playwright/UAT 全流程通过；
- 所有 10-19 阶段经独立 Reviewer PASS 后才允许签收。
