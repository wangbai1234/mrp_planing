# MRP Phase 2 第四轮验收报告与修复手册

**验收日期**：2026-09-23  
**验收对象**：Mimo 最近一轮完成的工作区代码  
**结论**：`FAIL / 暂不签收 Phase 2`

## 1. 验收结论

本轮不能签收。代码可以编译，前端单元测试和构建通过，但真实页面操作存在确定性 500，批次状态和结果守恒不一致，主业务入口仍是 Phase 1 周排产页，Phase 2 日排产工作台没有落地。`.harness/workflow.yaml` 的 10-19 阶段必须继续保持 `REVIEW`。

本报告是新增第四轮证据，不覆盖前三轮报告，也不把环境阻断或静态构建结果当作业务通过。

## 2. 本轮实际验证

| 项目 | 结果 | 证据 |
|---|---|---|
| 后端编译 | PASS | `backend/./mvnw compile -q` |
| 后端全量测试 | FAIL/环境阻断，同时暴露持久化测试污染风险 | 146 个测试中 1 个错误；`BomExplosionServiceTest` 无有效 Docker daemon。测试日志另见 `target/surefire-reports` |
| 前端单元测试 | PASS | `npm test -- --run`，2 个文件、15 个测试 |
| 前端构建 | PASS | `npm run build`，Vite 构建完成；存在 chunk 大于 500KB 的性能警告 |
| 差异检查 | PASS | `git diff --check` |
| Flyway | PASS | 本地测试/运行库已校验到 V029，29 个迁移有效 |
| API 健康检查 | PASS | `GET /actuator/health` 返回 `UP` |
| 登录 | PASS | `POST /api/v1/auth/login` 使用 `admin/admin123` 返回 200 |
| 现有 Playwright 登录/布局 | PASS（仅 Phase 1） | `npm exec playwright test e2e/login.spec.ts --project=chromium`，6/6；没有覆盖 Phase 2 批次、决策、日排产、发布 |
| 批次查询 | PASS | `GET /api/v1/mrp/batches` 返回 200 |
| 新增批次真实联调 | **FAIL/P0** | 前端只传 `factoryCode`，后端要求 6 个版本参数，实际返回 HTTP 500 `MRP_INTERNAL_ERROR` |
| 批次 35 状态检查 | **FAIL/P0** | `READY`，但 `pendingUnallocated=5` |
| 批次 35 守恒检查 | **FAIL/P0** | 需求 500，日计划 386.363640，`mismatchCount=1` |
| 浏览器页面审计 | **FAIL/P1** | `/planning` 仍是旧 Phase 1 W1-W4 页面；`/mrp/batches/:id` 仅为批次详情表，不是 Phase 2 日排产工作台 |

## 3. P0 阻断问题

### P0-401：新增批次前后端契约断裂，页面必现 500

证据：

- [frontend/src/api/mrp.ts](/Users/wanglixun/Project/MRP新版/frontend/src/api/mrp.ts:240) 的 `createBatch` 只发送 `factoryCode`。
- [MrpBatchController.java](/Users/wanglixun/Project/MRP新版/backend/src/main/java/com/mrp/mrp/controller/MrpBatchController.java:35) 强制要求 `forecastVersionId`、`inventorySnapshotId`、`bomSnapshotId`、`categoryRoleVersionId`、`materialPolicyVersionId`、`productionCalendarVersionId`。
- 页面 [MrpBatchList.vue](/Users/wanglixun/Project/MRP新版/frontend/src/views/mrp/MrpBatchList.vue:70) 没有版本选择或加载默认有效版本的逻辑。

影响：业务从页面点击“新增批次”无法创建批次；页面错误提示是通用“创建失败”，没有指出缺少哪一个输入版本。

修复要求：

1. 明确唯一契约：创建请求必须提交全部已固化版本 ID，或者提供后端只接收工厂并原子选择“当前已发布有效版本”的接口；不得让前端和后端各自猜默认值。
2. 推荐页面打开创建弹窗时加载经营计划、库存快照、BOM 快照、分类角色、物料策略、生产日历的有效版本，并要求业务确认后发送。
3. 后端校验版本存在、状态为可用、工厂一致、快照 checksum 可读；错误返回 4xx 业务错误，不得变成 500。
4. 删除或重写前端仍未使用的 `lockAndCalculate`、可覆盖 snapshot/rootDemands 的旧 `calculate` 契约，不能保留会绕过批次固化输入的入口。
5. 增加 API 契约测试和真实浏览器用例：创建成功、缺版本 400、跨工厂版本 409/422、重复 requestKey 幂等。

### P0-402：READY 状态与待分配/守恒结果矛盾

真实证据：

```text
GET /api/v1/mrp/batches/35/summary
status=READY, pendingUnallocated=5, dailyPlanCount=17

GET /api/v1/mrp/batches/35/plans/conservation
demandQuantity=500.000000, planQuantity=386.363640, mismatchCount=1
```

代码证据：[BatchOrchestratorService.java](/Users/wanglixun/Project/MRP新版/backend/src/main/java/com/mrp/mrp/service/BatchOrchestratorService.java:319) 和 [MrpTaskWorkerService.java](/Users/wanglixun/Project/MRP新版/backend/src/main/java/com/mrp/mrp/service/MrpTaskWorkerService.java:193) 在分配后仍无条件设置 `READY`。虽然发布接口再次检查待分配，但批次列表和详情已错误地向业务展示可发布状态。

修复要求：

1. `READY` 的不变量必须是：active run 存在、无 OPEN/BLOCKING/ERROR issue、库存分配已确认、待分配数量为 0、所有来源月守恒、日分配事务完整。
2. 分配结果有待分配时进入 `WAITING_DECISION` 或 `WAITING_DISTRIBUTION`，不能进入 READY。
3. Worker 不得覆盖计算/分配服务决定的状态；状态转移必须带当前状态、version、activeRunId 的 CAS 条件。
4. 发布前再次按 active run 执行 issue、待分配和守恒检查；失败返回可识别业务错误码。
5. 增加“阻断 issue”“待分配”“守恒不一致”“正常 READY”四组状态机测试，并以真实 API 验证列表、详情、发布按钮一致。

### P0-403：日/月守恒键不一致，跨日期计划被误判或漏判

证据：[DailyPlanningService.java](/Users/wanglixun/Project/MRP新版/backend/src/main/java/com/mrp/mrp/service/DailyPlanningService.java:203) 的 demand key 使用 `sourceMonth`，而计划 key 在 [同文件](/Users/wanglixun/Project/MRP新版/backend/src/main/java/com/mrp/mrp/service/DailyPlanningService.java:210) 将 `sourceMonth` 格式化为 ISO 日期；真实批次因此产生需求 500、计划 386.363640 的守恒异常。需要先确认字段类型后统一为同一 `yyyy-MM` 业务键，并区分来源月与最终排产日。

修复要求：统一 `factory + root + terminal + terminalType + supplyMode + sourceMonth + pathGroupKey` 的稳定聚合键；守恒必须比较有效数量（人工覆盖后为 `COALESCE(manual, system)`），而不是只比较系统数量；写入独立的守恒异常并阻断 READY。

### P0-404：active run 隔离仍不完整

部分服务已按 `activeRunId` 查询，但仍存在按 `batchId` 的结果读取：`getTerminalDemandsAggregated`、待分配查询、库存池/分配、执行记录、issue 和 override 等 Mapper/Service 仍暴露 batch 维度读写。月中重算、失败重算或修订后可能混入旧 run。

修复要求：批次结果 API 默认解析并校验 active run；所有需求、末级、日计划、待分配、守恒、导出、执行和分配 SQL 带 `run_id`。历史结果只能通过显式 `runId` 查询。增加“两次成功重算、一次失败重算、旧 active run 保留且 API 只返回新 active run”的集成测试。

## 4. P1 阻断问题

### P1-401：主业务入口和页面信息架构仍不符合 Phase 2

证据：[frontend/src/router/index.ts](/Users/wanglixun/Project/MRP新版/frontend/src/router/index.ts:10) 默认跳转 `/planning`，该页面仍为旧周排产 W1-W4；Phase 2 只有 [MrpBatchList.vue](/Users/wanglixun/Project/MRP新版/frontend/src/views/mrp/MrpBatchList.vue) 与 [MrpBatchDetail.vue](/Users/wanglixun/Project/MRP新版/frontend/src/views/mrp/MrpBatchDetail.vue)。

缺失项：

- 日排产唯一事实主网格，默认 28 天，按自然周分组；
- 固定列“来源整机”“末级需求物料”“终点/供应”“工厂”；同一末级来自不同整机必须是不同稳定行；
- 日单元格编辑、人工数量/系统数量、锁定/已执行保护；
- 周汇总、月汇总只读页；
- 业务决策中心、库存分配工作台、生产日历维护、执行确认；
- 发布后生成独立修订版本和版本对比；
- 搜索、服务端分页、空/加载/错误状态和 Phase 2 Playwright/UAT。

详情页当前日排产表还展示“来源日”“排产日”两列（[MrpBatchDetail.vue](/Users/wanglixun/Project/MRP新版/frontend/src/views/mrp/MrpBatchDetail.vue:63)），这会重新引入“来源需求周/实际排产周”的歧义。主日网格应只展示最终排产日期列；来源日期/月只放在追溯抽屉或详情中。

修复要求：按 `docs/MRP日排产页面设计方案.md` 和 `prototype/` 重做 Phase 2 路由与页面，所有组件使用 Element Plus 和真实 API；不要把现有批次详情表包装成日排产工作台。

### P1-402：列表违反服务端分页和筛选契约

`getBatches(status?)` 只返回数组，前端没有页码、每页大小、关键词、工厂和创建时间筛选；当前数据库已产生大量重复 DRAFT 批次。按 `.harness/list-rules.md` 需要服务端分页、总数、筛选条件、加载/空/错误状态。

### P1-403：执行/锁定与修订链路尚未形成可验收闭环

需要复核并补齐真实 `rowKey/dailyPlanId` 绑定、实际数量与锁定计划差异异常、LOCKED/EXECUTED 日期保护、月中重算仅处理未来未锁定日期、修订独立 batch/run 和库存分配口径选择。不能只存在 controller 或 service 骨架而没有 E2E 证据。

### P1-404：任务 lease/失败清理和 CAS 仍缺真实证明

现有日志显示 lease 字段已经存在，但必须测试 claim 的 `worker_id/lease_until/attempt_count`、heartbeat、租约过期接管、失败 run 不删除旧 active run，以及错误版本号不会把任务误标成功。Docker 不可用时记录环境阻断，不能跳过这些测试。

### P1-405：库存/BOM确定性边界未全部闭合

- `MrpCalculationService` 仍以工厂代码作为默认 `inventoryOrg`，不符合库存组织不按明细行映射、快照按物料汇总但区分 GOOD/DEFECTIVE 的已裁决口径；应在快照级明确组织和质量汇总规则。
- 缺库存记录与明确零库存、DEFECTIVE 不可用、共享库存分配的业务决策必须可追溯，并在未确认时阻断计算。
- BOM 同步应按“库存组织 + 父料号 + 子料号”只保留最高有效版本；同键多个最高版本、有效期重叠、组织不明要阻断。MRP 只读已固化快照。

## 5. 测试污染问题

全量测试除了 Testcontainers 环境错误，还出现 `mrp_decision_issue.uk_mdi_batch_issue_key` 重复键异常（批次 999999 / issue TEST-001）。这说明测试数据没有可靠清理或 issue 写入未实现幂等。修复：每个测试使用唯一业务键或事务/fixture 清理；issue upsert 必须按稳定 `issue_key` 和 run/batch 口径设计；不得通过清空业务库或放宽唯一约束规避。

## 6. 修复顺序

1. 先修 P0-401 至 P0-404：冻结 API 契约、状态不变量、守恒键、active run 单一真源。
2. 再修任务 lease/失败清理、库存/BOM阻断和执行/修订保护。
3. 重做 Phase 2 前端工作台及主入口，补服务端分页和真实 API 联调。
4. 增加 Docker 可用环境中的全量后端测试、Phase 2 Playwright、权限、并发、迁移和 UAT。
5. 独立 Reviewer 复核后，才可把 10-19 从 `REVIEW` 改为 `DONE`。

## 7. Mimo 回归通过标准

- 页面新增批次不再 500，版本输入、校验和错误码可见；
- 任一 issue、待分配或守恒异常都不能显示 READY 或出现发布按钮；
- 两次重算、失败重算和修订结果严格按 active run 隔离；
- 真实日排产页默认 28 天，固定双料号列和日事实编辑，周/月只读聚合；
- 已锁定/已执行日不可被重算或覆盖，执行差异可追溯；
- 后端全量测试在 Docker 可用环境通过，前端测试/构建/类型检查通过；
- API、浏览器、Playwright、空库迁移、V020 升级、权限和 UAT 均有最新证据；
- 不修改 V001-V029，只能新增 V030+；不排除测试、不修改断言、不清库伪造 PASS。
