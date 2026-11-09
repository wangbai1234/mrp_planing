# 库存分配明细续修报告

## 状态

DONE

## 本次完成

- 用户授权对测试数据操作：作废旧批次 116 并保留历史，新建永惠工厂批次 117（经营计划 V3、库存快照 6）。
- 页面把 5044AA800009 的 2026-10 来源需求由自动 450 调为人工 400；重分析后批量确认 19 条缺库存记录，执行 MRP 计算，active run 69。
- 修复恢复自动分配：清除人工标记及原因，更新分配和库存池的乐观锁，批次进入 REANALYZING。人工修改及恢复均写审计。新增恢复按钮及状态提示，重新分析前禁用缺库存批量确认。
- 增加恢复自动的服务测试和前端 API 路径测试。
- 保留自动库存分配结果的展示、人工调整原因、revision 乐观锁和重新分析状态流转。
- 为库存池详情、池分配明细、批次分配明细补齐工厂数据域校验，避免通过明细查询绕过排产版本权限。
- 新增控制器回归测试，覆盖三个只读入口的资源校验调用。

## 修改文件

- backend/src/main/java/com/mrp/mrp/service/InventoryNormalizationService.java
- backend/src/test/java/com/mrp/mrp/service/InventoryNormalizationServiceTest.java
- frontend/src/api/mrp.ts、frontend/src/api/__tests__/mrp-query.test.ts
- frontend/src/views/mrp/MrpWorkspace.vue、frontend/src/views/mrp/components/InventoryAllocation.vue、frontend/src/views/mrp/components/DecisionCenter.vue
- `backend/src/main/java/com/mrp/mrp/controller/MrpInventoryController.java`
- `backend/src/test/java/com/mrp/mrp/controller/MrpInventoryControllerTest.java`

## 业务不变量

- 批次 117 的 5044AA800009 库存 500、安全库存 50、可分配 450；人工 400 加自动 50 等于 450，未侵占安全库存。
- 恢复自动仅允许未计算的可重分析批次；批次 READY 时页面禁用调整和恢复。
- 用户只能读取其有权限的排产版本及其所属工厂的库存池和分配明细。
- 自动分配按冻结经营计划来源月和优先级生成；人工调整必须填写原因并携带当前 revision。
- 人工调整后批次进入 `REANALYZING`，重新预检才生成新的自动分配结果。

## 验证命令与结果

```text
cd backend && ./mvnw compile -q
通过

cd backend && ./mvnw test -q -Dtest=MrpInventoryControllerTest,InventoryNormalizationServiceTest,NetInventoryAllocationTest,BatchOrchestratorServiceTest
通过；聚焦测试全部通过，新增控制器测试 3/3 通过

cd frontend && npm run build
通过

cd frontend && npm test
通过；12 个测试文件、99 项测试通过

真实联调：POST /api/v1/auth/login；GET /api/v1/mrp/inventory/pools/116；GET /api/v1/mrp/inventory/allocations/pool/37237；GET /api/v1/mrp/inventory/allocations/batch/116
通过；登录成功，库存池返回 6 条，两个分配查询返回 200 和空数组，未写入业务数据

浏览器：Chrome 登录 http://localhost:5180，打开排产中心批次 116 的“异常与明细 → 库存分配明细”
通过；显示库存池、安全库存、可分配数量、剩余可分配和空状态
```

## 未完成与风险

- 批次 117 有 49 项发布前异常，其中 24 项默认继续下钻待确认；发布按钮禁用，未发布。
- 恢复自动已由服务测试验证，当前批次 117 是 READY，页面有意禁用恢复；未另建批次进行恢复按钮写入联调。

## 2026-10-23 续验

- 修改文件：backend/src/main/java/com/mrp/mrp/service/InventoryNormalizationService.java、backend/src/test/java/com/mrp/mrp/service/InventoryNormalizationServiceTest.java、frontend/src/api/mrp.ts、frontend/src/api/__tests__/mrp-query.test.ts、frontend/src/views/mrp/MrpWorkspace.vue、frontend/src/views/mrp/components/InventoryAllocation.vue、frontend/src/views/mrp/components/DecisionCenter.vue。
- backend 运行 ./mvnw compile -q 通过；定向 ./mvnw test -q -Dtest=InventoryNormalizationServiceTest,MrpInventoryControllerTest 通过。frontend 运行 npm run build 通过，npm test 12 文件 100 项通过。
- 重启 local 后端并用浏览器查看 localhost:5180/planning：库存分配显示人工 400、自动 50、保留 50；计算追溯 2026-10 毛需求 600、耗库 400、净需求 200。数据库 2026-11 毛需求 1200、耗库 50、净需求 1150；5051AA800042 有缺库存 issue。未经登录的 API 返回 401，符合认证门禁。

- 后端全量 ./mvnw test -q 已通过（85 个测试套件、606 项测试、0 失败、0 错误、0 跳过；Testcontainers MySQL 8）。

## 决策/待确认

- 本次权限、恢复自动、状态交互可进入独立阶段复核；阶段状态保持 `REVIEW`。

## 下一步

- 独立 Reviewer 复核权限链路和库存分配写入事务后，再决定是否将该补充任务标记为 DONE。

## 2026-10-23 独立复核失败后的续修

- 原独立 Reviewer 判定 FAIL：库存池创建、分配预览及提交入口可绕过页面写入；人工调整可伪造审计操作者；调整、恢复和预检并发锁不统一。保留原失败证据。
- 已移除三个直接写入的对外入口和前端无用调用；保留批次创建时内部生成库存池及预检时自动分配。
- 人工调整和恢复现在审计登录用户；先锁批次行，再重读分配和库存池，校验状态与 revision。新增真实 MySQL 并发测试。
- 真实页面：创建永惠批次 118（经营计划 V3、库存快照 6）时将待发布批次 117 作废并保留历史，117 原 active run 为 69。118 中物料 5044AA800009 的 2026-10 分配自动 450 → 人工 400 → 恢复自动后 0 → 重新分析后自动 450；库存 500、安全库存 50，池分配 450。审计含人工调整和恢复。
- MySQL 再核对：117 为 CANCELLED、run 69；118 为 WAITING_DECISION、无 active run，19 条 INVENTORY_MISSING 均为 OPEN；物料 5044AA800009 分配和池分配均为 450。尚未计算或发布。
- 编译、定向测试及最终串行全量测试通过；Surefire XML 共 86 套件、607 测试、0 failure、0 error、0 skipped。前端构建和 100 项测试通过。后端重启后登录、118 查询和浏览器刷新通过；三个删除的 POST 返回 404/405/405。
- 当前代码再核对：预检、人工调整及恢复均调用批次 selectForUpdate；分配控制器只保留人工调整、恢复及只读入口。

### 仍待完成

- 原独立 Reviewer 第二次只读复核判定 PASS：旧写入入口、登录用户审计、批次锁及真实 MySQL 并发回归和页面闭环均通过。本补充任务由 Coordinator 置 DONE。
- 批次 118 的缺库存处理与计算见下文。发布另有物料策略、库存风险等门禁，不能预先宣称可发布。
- 本地后端健康检查发现 Oracle 数据源连接超时，需区分此项与 MySQL 业务接口状态。

## 批次 118 计算联调

- 登录后 GET /api/v1/mrp/batches/118/issues?status=OPEN 返回 19 条，全部为 INVENTORY_MISSING。POST /api/v1/mrp/batches/118/issues/inventory-missing/resolve 批量确认按零库存计算，HTTP 200，摘要转 READY_TO_CALCULATE、openIssues=0。
- POST /api/v1/mrp/batches/118/calculate 返回任务 195；随后 GET /calculation-task 为 SUCCEEDED、100/100、COMPLETED。摘要为 READY、日计划 3288 条、openIssues=0、未发布；MySQL 批次 active run=70、run COMPLETED，19 条异常均 RESOLVED。
- run 70 需求明细 168 条：毛需求 303000、耗库 2700、净需求 300300，逐行汇总 gross-used-net=0。5044AA800009 在 2026-10 毛需求 600、耗库 450、净需求 150。日计划 3288 条、system_quantity 合计 293650，两个总量口径不同。
- 发布前风险仍有 5 条 SAFETY_STOCK_BREACH 和 20 条 STOCK_POLICY_MISSING，均未处理；没有发布。
- Chrome 刷新 /planning/118：页面显示“待发布”、计算结果已通过门禁、日排产非空且有 5051AA800042；“发布排产结果”仍禁用，并提示 24 个默认继续下钻待确认及 49 项异常待处理。与 API 的 READY/未发布一致。
