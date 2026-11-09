# MRP Phase 2 第二轮验收报告与修复手册

**验收日期**：2026-09-23  
**验收结论**：`FAIL / 暂不签收`  
**审计对象**：Mimo 修复后的当前工作区  
**上一轮报告**：[MRP Phase2验收报告与完整修复手册.md](MRP%20Phase2验收报告与完整修复手册.md)

## 1. 本轮结论

本轮确认上一轮的 Flyway checksum 阻断已被部分处理，但 Phase 2 仍不能签收：

- `backend/./mvnw clean compile -q`：通过；
- `backend/./mvnw test -q`：失败，125 个测试中 1 个错误；`BomExplosionServiceTest` 依赖 Testcontainers，但当前 Docker daemon 不可用。不能用排除该测试的命令作为全量通过证据；
- `frontend/npm test -- --run`：通过，2 个文件、15 个测试；
- `frontend/npm run build`：通过；
- `git diff --check`：通过；
- Spring 测试日志显示 `mrp`、`mrp_test` 均可验证到 V025，上一轮 V021/V022 checksum 阻断已不再出现；但本轮没有完成独立空库和 V020 升级副本的迁移证据；
- 没有发现可签收的 Phase 2 专项算法测试、API 契约测试、执行锁定测试、修订测试或完整 Playwright E2E 证据。

因此，`.harness/workflow.yaml` 中 10-19 仍置为 `DONE` 与真实验收不一致。修复期间应回退到 `REVIEW`，保留失败证据，完成修复后由独立 Reviewer 再次复核。

## 2. 已确认的改进

以下改动已经存在，不能误报为未实现：

1. V023 增加了 `inventory_item.inventory_quality`、库存数量 DECIMAL 和 BOM 快照错误信息；
2. V024 增加了 `mrp_inventory_balance.inventory_quality`、批次修订字段和 `mrp_daily_execution` 表；
3. V025 增加了部分 MRP 权限种子；
4. `InventoryItem`、库存模板和 SAX 解析器已经出现 `GOOD/DEFECTIVE` 字段；
5. `NetRequirementEngine` 已出现 STOP/EXPLODE、路径循环检查和 BigDecimal 计算骨架；
6. 日排产 API 已出现分页参数和日网格响应骨架；
7. 发布逻辑已出现“创建修订批次”的意图。

这些属于结构性进展，但尚未形成端到端、可复现、可阻断错误的业务闭环。

## 3. P0 阻断问题

### P0-201：全量后端门禁仍失败，Docker 依赖测试被静默排除

**证据**：`./mvnw test -q` 实际执行 125 个测试，`BomExplosionServiceTest` 因 Testcontainers 无可用 Docker daemon 报错；历史报告使用 `-Dtest='!BomExplosionServiceTest'` 后写成 124/124 PASS。

**影响**：BOM/数据库真实行为没有被验证，报告不能称为全量通过。

**修复要求**：

1. 不修改测试断言、不删除测试、不再使用排除测试命令冒充全量 PASS；
2. 在 CI/UAT 提供可复现的 MySQL/Testcontainers 环境，或把 BOM 集成测试改为项目契约允许的独立 MySQL 测试配置；
3. 重新执行完整 `./mvnw test`，保存退出码、测试总数和失败详情；
4. 如当前机器无法提供 Docker，只能在报告中标记环境阻断，不能标记 Phase 2 完成。

### P0-202：预检实际对空需求运行，不能发现任何物料/BOM/库存问题

**证据**：[BatchOrchestratorService.java](../backend/src/main/java/com/mrp/mrp/service/BatchOrchestratorService.java) 的 `precheck` 构造 `Collections.emptyList()` 传给 `NetRequirementEngine`。因此缺分类角色、缺物料策略、EXPLODE 无 BOM、循环、库存缺失等问题不会被遍历；预检可能直接将批次置为 `READY_TO_CALCULATE`。

**修复要求**：

1. 批次创建时固定并读取经营计划版本中的整机月需求，预检必须使用该版本的全部 root demand；请求体不能临时替换已绑定输入；
2. 预检真实执行 BOM、角色、策略、库存、生产日历、共享库存决策和非生产日检查；
3. 生成 issue 必须幂等，重新预检不能重复插入同一 `issueKey`；已解决决策要按 revision/checksum 重新判定；
4. 只有不存在 BLOCKING、缺库存已明确决策、待分配已解决且输入 checksum 未变化时，才允许 `READY_TO_CALCULATE`。

### P0-203：计算结果中的阻断 issue 被丢弃，缺库存被当成 0

**证据**：[MrpCalculationService.java](../backend/src/main/java/com/mrp/mrp/service/MrpCalculationService.java) 只写入 requirements 和 terminal demands，没有持久化 `ExplosionResult.issues`，也没有根据 issue 阻断 run。  
[InventoryNormalizationService.java](../backend/src/main/java/com/mrp/mrp/service/InventoryNormalizationService.java) 只汇总正数 `shippingAvailableQty`，且 [MrpInventoryBalanceMapper.xml](../backend/src/main/resources/mapper/mrp/MrpInventoryBalanceMapper.xml) 的 `selectBySnapshotId` 只返回 `included_flag=1`，缺少库存记录时 `NetRequirementEngine` 使用 `getOrDefault(..., ZERO)`，无法区分“确实为 0”和“没有记录”。

**影响**：业务明确要求“缺库存记录由业务确认”，当前会静默按 0 继续计算；DEFECTIVE 追溯数据也在查询层被过滤掉。

**修复要求**：

1. GOOD 余额进入扣减；DEFECTIVE 保留查询和追溯，但永不进入可用量；查询接口不能为了 MRP 过滤掉 DEFECTIVE；
2. 以 `snapshot + factory + inventory_org + material + quality` 汇总，并保留零数量/来源行所需的审计信息；
3. 缺少物料记录时生成 `INVENTORY_MISSING`，不得默认为 0；业务确认后才允许计算，并把决策写入 decision checksum；
4. 计算必须持久化全部 issue，并在存在 BLOCKING issue 时将 run/batch 置为失败或等待决策，不能返回 READY；
5. 增加 GOOD/DEFECTIVE、零库存、缺记录、缺库存确认和 issue 持久化测试。

### P0-204：逐层库存扣减和共享库存分配仍未接入正式计算

**证据**：`MrpCalculationService` 传入 `Collections.emptyList()` 作为 locked allocations；库存 map 只按 `materialCode` 汇总，忽略工厂、库存组织、品质和业务分配。`NetRequirementEngine` 通过遍历顺序扣减共享库存，不能证明业务锁定分配已经生效。

**修复要求**：

1. 计算输入必须读取同一批次已锁定的库存分配记录，按 `demandKey/pathHash` 应用，禁止遍历顺序代替业务决策；
2. 同一物料跨整机、跨路径、跨来源月竞争库存时必须阻断，直到分配工作台提交并锁定；
3. 每层保存 gross、库存前、使用量、库存后、net、来源整机、路径和终止原因；
4. 用黄金示例验证：整机 1000、整机库存 100、B 库存 200、C 库存 300、B->D 库存 200，逐层结果必须可解释且守恒；
5. 增加 STOP、EXPLODE 无 BOM、循环 A->B->A、共享库存和小数比例测试。

### P0-205：BOM 最高有效版本和有效期裁决仍不符合确认规则

**证据**：[BomSnapshotService.java](../backend/src/main/java/com/mrp/mrp/service/BomSnapshotService.java) 以 `effective_date` 最新值作为“最高版本”，没有按版本值裁决；有效期重叠检查仅比较日期和版本列表，不能表示真实有效起止区间；`organization_code` 与 `body_scope` 的业务含义混用；快照 checksum 未包含组织、版本、有效日期等全部字段。

**修复要求**：

1. NC 同步先按 `inventory_org + parent_code + child_code` 形成业务键，再按明确版本排序只保留最高有效版本；版本值必须是可比较的规范字段；
2. 同一键多个最高版本、有效期重叠、组织为空、父子数量非法时快照必须 `FAILED/WAITING_DECISION`，不能 READY；
3. MRP 只读取已固化且 checksum 固定的快照，计算请求传入的 snapshot id 必须等于批次绑定值；
4. checksum 必须稳定包含组织、父子料号、数量、版本、生效/失效日期；
5. 增加组织隔离、最高版本、重复最高版本、有效期重叠和快照不可变测试。

### P0-206：任务流仍同步执行，输入未绑定，失败可能留下半成品

**证据**：[BatchOrchestratorService.java](../backend/src/main/java/com/mrp/mrp/service/BatchOrchestratorService.java) 在创建 `calc_task` 后直接调用 `calculationService.executeCalculation`，并明确保留 `TODO: Move to async worker`。`inputPayload` 只有 batchId；`submitCalculation` 接收的 BOM、库存和 rootDemands 可以与批次绑定值不一致。计算写入 requirements/terminal 后才更新 run 和 batch，不能证明失败清理和 active run 原子切换。

**修复要求**：

1. HTTP 只创建 PENDING task 并立即返回 taskId；Worker 负责认领、lease、重试、幂等和失败恢复；
2. task payload 必须保存批次绑定的所有输入版本和 root demand checksum，不能信任请求覆盖；
3. 使用短事务分块写结果；只有全部写入和校验成功才切换 `active_run_id`；失败删除/隔离本次 run 的半成品；
4. 计算前后校验 BOM、库存、策略、日历和决策 checksum 未变化；
5. 增加重复请求、并发认领、失败重试、半成品清理和 active run 切换测试。

### P0-207：日排产没有进入批次闭环，且唯一键/守恒不正确

**证据**：[DailyPlanningService.java](../backend/src/main/java/com/mrp/mrp/service/DailyPlanningService.java) 需要额外调用 `/distribute` 才生成日事实；计算完成不会自动分配，发布也没有要求存在日事实。  
[DailyDistributionEngine.java](../backend/src/main/java/com/mrp/mrp/engine/DailyDistributionEngine.java) 分组键只含末级物料、终点类型、供应方式，遗漏来源整机、来源月和路径；小数分配固定 6 位，整数分配假设 supply mode 文本包含 `INTEGER/UNIT`；非生产目标日同时写入日计划和待分配。

**修复要求**：

1. 日事实唯一键至少包括工厂、来源整机、末级物料、终点类型、供应方式、pathGroupKey、sourceMonth、scheduleDate；`rowKey` 使用契约约定的 SHA-256；
2. 计算成功后在同一批次编排中完成日分配，或明确状态 `WAITING_DAILY_DISTRIBUTION`，不得直接 READY/PUBLISHED；
3. 按物料策略 quantity scale 量化，残差按稳定日期顺序回补，任何情况下来源月总量守恒；
4. `scheduleDate = sourceDate - 7 calendar days`；非生产目标日进入待分配池，不应同时表现为有效排产事实；业务解决后才生成最终日事实；
5. 周/月只读聚合日事实，服务端提供周汇总、月汇总、来源月守恒接口；
6. 增加跨月、五个周一、节假日、非生产日、重复来源和小数守恒测试。

### P0-208：发布修订不是可用的修订版本，已执行/锁定保护未实现

**证据**：发布逻辑创建新批次并复制 `activeRunId`，但没有复制或重建该修订批次的日计划、待分配、执行状态和差异异常；没有创建修订 API；`mrp_daily_execution` 只有实体/Mapper，没有业务 Service、Controller 或前端入口；人工 override 不检查日期是否 LOCKED/EXECUTED；没有月中重算 API。

**修复要求**：

1. 发布后通过显式 revision API 创建新草稿，重新计算未来未锁定日期；旧发布批次和已执行/锁定日只读；
2. 修订必须有独立 run、daily plan、issues、decision checksum 和 result checksum，不能共享旧 run 作为结果身份；
3. 提供执行确认 API：业务手工写入 `PLANNED/LOCKED/EXECUTED` 和实际量；锁定日不能被重算、override 或修订覆盖；
4. 实际量与锁定时计划量不一致生成 `EXECUTION_QUANTITY_MISMATCH`；
5. 月中重算只处理未来未锁定日期，并在库存分配口径未决时生成差异预览并阻断发布；
6. 增加锁定、执行、差异、月中重算和发布修订测试。

### P0-209：权限仍存在越权和身份伪造入口

**证据**：[MrpBatchController.java](../backend/src/main/java/com/mrp/mrp/controller/MrpBatchController.java) 将 `createdBy` 固定为 `1L`；[MrpBatchLifecycleController.java](../backend/src/main/java/com/mrp/mrp/controller/MrpBatchLifecycleController.java) 允许请求传入 `operatorId`，缺省也使用 `1L`。权限虽然增加了若干 permission seed，但 GET MRP 统一要求 `mrp_batch:create`，没有按查看/决策/库存分配/执行/配置/发布区分；未见工厂数据域校验。

**修复要求**：

1. 所有操作者从当前 JWT/CurrentUser 获取，删除请求参数和硬编码用户 ID；
2. 后端细分 `mrp_batch:view/create/precheck/calculate/publish`、`mrp_decision:manage`、`mrp_inventory:allocate`、`mrp_plan:adjust/export`、`mrp_execution:confirm`、`mrp_calendar:manage`；
3. 每个查询和写入校验用户所属工厂数据域；前端权限只作体验控制；
4. 增加 401、403、跨工厂和无权限角色测试；
5. 清理 [backend/.env.example](../backend/.env.example) 和 `application-local.yml` 中真实格式 Feishu secret，改为无效占位符并轮换已暴露凭据。

## 4. P1 问题

### P1-201：前端仍不是最新 Phase 2 日排产工作台

当前仅有批次列表和详情页。详情页仍展示“来源日”和“排产日”双时间轴表格，缺少以最终排产日为主的日网格、周/月只读汇总、来源整机与末级需求物料固定列、决策中心、库存分配、生产日历、执行确认、修订/版本对比。`frontend/src/api/mrp.ts` 正式数量仍使用 TypeScript `number`，违反 DECIMAL 字符串契约；列表无服务端分页。

修复时必须读取 `prototype/`、`assets/import-design-rules.md` 和 `.harness/list-rules.md`，使用 Element Plus，补齐加载/空/错状态和真实 API 联调。不得用前端自行聚合替代服务端周/月结果。

### P1-202：报告和阶段状态未反映真实结果

`.harness/reports/19-docker-uat-final-audit-report.md` 仍写 V001-V021、Docker UAT PASS、发布 PASS；实际工作区存在 V023-V025，且本轮全量测试失败。`phase2-execution-summary.md` 仍把核心算法写成框架/待实现，和 workflow 全部 `DONE` 矛盾。

修复时必须保留历史报告，新增本轮修复前/修复后证据；不得覆盖失败记录或只改文案制造 PASS。所有 10-19 阶段完成后由独立 Reviewer 运行 FINAL-AUDIT，不能由实现 Agent 自评完成。

### P1-203：迁移和 UAT 证据仍不完整

当前只能从 Spring 日志确认已有数据库到 V025；未提供独立空库 V001->V025、现有 V020 副本升级、`flyway validate` 和 schema diff 的可复现日志。UAT 脚本历史上存在共享 volume 删除、固定本机路径和旧 Phase 1 API 风险，本轮未提供修复后完整脚本执行证据。

必须使用独立 compose project/volume，禁止对共享环境执行 `down -v`；UAT 只调用 Phase 2 API，凭据通过环境变量注入，保存请求、响应、退出码和数据库 history。

### P1-204：认证状态码与 API 契约不一致

**证据**：本轮启动真实服务后，`GET /api/v1/mrp/batches` 无 Authorization 返回 HTTP 403。`.harness/api-contract.md` 明确要求未认证 401、无权限/越工厂域 403；现有 `RoleBasedAccessTest`、`SecurityPermissionTest` 也把无 token 断言为 403，导致测试和实现共同偏离正式契约。

**修复要求**：

1. 未携带 token、token 无效或过期统一返回 401；携带有效身份但权限不足或工厂域越权返回 403；
2. 统一 Spring Security `AuthenticationEntryPoint` 和 `AccessDeniedHandler` 的 JSON 错误结构；
3. 更新安全测试为 401/403 分层断言，并在真实 curl/UAT 中保存证据；
4. 前端分别处理登录失效和权限不足，不能把两者都显示成同一错误。

## 5. Mimo 执行顺序与文件边界

1. 先修 P0-201 运行门禁和迁移/UAT证据；
2. 再修 P0-202 至 P0-205（预检、库存、算法、BOM）；
3. 再修 P0-206 至 P0-209（异步、日事实、执行/修订、权限）；
4. 最后修 P1 前端、报告和 E2E；
5. 每个 Agent 先声明互斥文件所有权，迁移、共享 DTO、SecurityConfig、工作流只能由一个 Agent 修改；
6. 实现 Agent 完成后必须由独立 Reviewer 只读复核，失败回到 `REVIEW`，不能直接置 `DONE`。

## 6. 强制验收命令

```bash
git diff --check

cd backend
./mvnw clean compile
./mvnw test

cd ../frontend
npm run build
npm test -- --run
npx vue-tsc --noEmit

# 独立 MySQL 空库和 V020 升级副本
flyway validate
flyway migrate

# 启动 API 后验证真实请求
curl -fsS "$BASE_URL/actuator/health"
# 至少覆盖：未登录 401、无权限 403、批次创建、预检、决策、库存品质、BOM 冲突、异步 task、日网格、周/月汇总、执行确认、修订和发布阻断

cd ../frontend
npx playwright test
```

不能使用 `-Dtest='!BomExplosionServiceTest'` 作为全量测试结论，不能因 Docker/Node 环境不满足而修改项目版本配置；环境问题必须按 AGENTS.md 停止并报告。

## 7. 最终签收标准

- 全量后端测试通过，或环境阻断有明确负责人和独立复现记录；
- 预检能用真实经营计划发现所有阻断问题；
- GOOD/DEFECTIVE、缺库存、共享库存分配和逐层扣减有黄金测试；
- BOM 最高版本、组织隔离和冲突阻断有测试；
- 计算异步、幂等、失败清理和输入 checksum 有运行证据；
- 日事实自动生成且来源月守恒，跨月/节假日/非生产日正确；
- 锁定/执行日不可覆盖，月中重算新 run，发布修订独立且旧版本只读；
- 日排产工作台、决策、库存分配、生产日历、执行确认和修订页面可真实联调；
- 权限、工厂域、secret、迁移空库/升级和 Playwright 全部通过；
- 独立 Reviewer `PASS` 后，才允许将 10-19 和 FINAL-AUDIT 置为 `DONE`。
