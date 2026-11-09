# MRP Phase 2 验收报告与完整修复手册

**验收日期**：2026-09-23  
**验收结论**：`FAIL / 暂不签收`  
**范围**：Phase 2 多层净需求、库存快照、BOM 快照、日排产、批次生命周期、前端工作台、迁移、权限和测试

## 1. 结论

当前实现不能认定为 Phase 2 完成。Mimo 报告中的“编译通过、测试通过、完整生命周期通过”与当前工作区真实状态不一致：

- 后端 `./mvnw compile -q` 通过；
- 前端 `npm run build` 通过；
- 前端 Vitest 通过：2 个文件、15 个测试；
- 后端 `./mvnw test` 失败，Spring Context 因 Flyway V021/V022 checksum mismatch 无法启动；
- `./mvnw clean compile` 还曾因运行中的静态资源文件占用而失败；
- 没有可验收的 Phase 2 专项单元测试、API 契约测试或 Playwright E2E；
- 页面和 API 仍缺少日网格、周/月汇总、业务决策、库存分配、执行确认和修订批次等冻结范围。

在 P0/P1 问题修复、独立复核和真实浏览器联调完成前，不得将 workflow 标记为 `DONE`，不得发布 Phase 2。

## 2. 验收证据

### 2.1 已执行命令

```text
cd backend && ./mvnw compile -q
结果：通过

cd backend && ./mvnw test
结果：失败。mrp_test 的 Flyway 校验失败：
Migration checksum mismatch for migration version 021
Applied: 1618624272 / Resolved locally: -1460648612
Migration checksum mismatch for migration version 022
Applied: 1330388513 / Resolved locally: -272052641

cd frontend && npm run build
结果：通过

cd frontend && npm test -- --run
结果：通过，2 个测试文件、15 个测试

git diff --check -- .harness backend frontend docs assets deploy project.md
结果：失败，至少 4 处新增 trailing whitespace
```

### 2.2 数据库实测

`mrp_test.flyway_schema_history` 已记录 V021/V022，但当前工作区脚本与已应用 checksum 不一致；`mrp_test` 的 `mrp_inventory_balance` 没有 `inventory_quality`，`mrp_calc_batch` 没有修订字段，也没有 `mrp_daily_execution`。生产开发库 `mrp` 是通过人工修改补齐的，不能作为可迁移、可重建的证据。

### 2.3 报告可信度问题

`.harness/reports/19-docker-uat-final-audit-report.md` 仍只写 V001-V021，工作区实际存在 V022；`phase2-execution-summary.md` 仍保留“待业务决策”的历史表述，且报告称全流程通过，但当前全量测试无法加载 Context。修复期间必须将阶段状态降为 `REVIEW` 或 `BLOCKED`，重新生成报告，不能沿用旧报告的 PASS 结论。

## 3. 阻断问题清单

### P0-001：Flyway 迁移不可验证，测试库无法启动

**证据**：当前测试直接报 V021/V022 checksum mismatch。V022 文件主体只是注释和 `SELECT`，并没有创建它声称补齐的字段和表。

**影响**：新环境、测试环境和升级环境不能保证得到同一 schema；后端测试和服务启动被阻断，违反“迁移只新增、已应用迁移不可修改”。

**必须修复**：

1. 先保存当前数据库 `flyway_schema_history`、所有 V021/V022 的 applied checksum、`SHOW CREATE TABLE` 和备份，禁止直接执行 `flyway repair`。
2. 找回 V021/V022 当时实际应用的原始脚本；若无法找回，必须由数据库结构反推一份经过评审的基线，并在独立升级方案中说明 checksum 恢复策略，不能静默覆盖历史。
3. 已应用的 V001-V022 不得改名、重编号、删除或修改；所有新增差异只能放入新的 V023+ 迁移。V023 必须真实创建 `inventory_quality`、修订字段、`mrp_daily_execution`、必要索引和约束，并可在空库执行。
4. 在全新 `mrp_test` 和从现有 V022 升级的副本上分别执行 `flyway validate/migrate`；两条路径都必须通过。
5. 重新运行 `./mvnw test`，禁止再用排除核心测试的命令作为验收结论。

### P0-002：库存品质没有贯穿模板、解析、实体和 MRP 真源

**证据**：

- `TemplateGenerator.generateInventoryTemplate()` 仍是旧库存模板，没有 `inventory_quality`；
- `InventoryExcelParser` 只解析 6 列，数量使用 `long`，并通过仓库名称猜测不良品；
- `InventoryDetail` 没有 `inventoryQuality`；
- 解析结果没有按规范保存完整 `rawRows/headers`，也没有可靠处理稀疏单元格；
- `InventoryNormalizationService` 实际读取 `InventoryItem.shippingAvailableQty`，统一写成 `GOOD`，绕过了新库存明细；
- `mrp_test` schema 没有该字段。

**影响**：机头、PCBA、原材料库存不能按良品/不良品正确汇总；不良品可能被错误扣减；模板、错误报告和数据库不一致。

**必须修复**：

1. 库存快照头维护工厂、库存组织、快照日期；明细增加必填 `inventoryQuality`，只接受 `GOOD/DEFECTIVE`。
2. 模板顺序固定为表头、提示行、示例/数据，并增加品质列；错误原因放最后一列。
3. 解析使用 `BigDecimal`，保存 `rawRows`、`headers`，补齐稀疏列；缺失、未知品质和负数必须阻断整份导入。
4. 正式实体、预览、错误报告、规范化余额、查询 API、前端表格都返回品质；按“快照 + 物料 + 品质”汇总。
5. 只有 `GOOD` 进入 `available` 和 MRP 扣减；`DEFECTIVE` 保留追溯，不参与扣减；不得通过仓库名称推断品质。
6. 增加模板、解析、错误报告、GOOD/DEFECTIVE 汇总和不良品不扣减测试。

### P0-003：净需求引擎没有执行物料策略和业务决策

**证据**：`NetRequirementEngine` 构造参数只有 BOM edges 和库存 map，没有分类角色、物料策略、供应方式或决策记录；代码只在“没有子项”时终止，未读取 `STOP/EXPLODE`；终端统一写成 `STRUCTURAL_LEAF/UNMAINTAINED`；循环键是 `materialCode + depth`，A→B→A 不会在回到 A 时被识别，只会运行到最大深度。

`MrpCalculationService` 把所有库存按物料简单合并，忽略工厂、库存组织、品质、库存池分配和“缺记录必须业务确认”；计算没有生成对应的阻断问题。

**影响**：外购/委外 STOP、机头 STOP、PCBA STOP、EXPLODE 无 BOM、未维护物料和共享库存竞争均可能得到错误的“可计算”结果。

**必须修复**：

1. 计算输入必须绑定已发布分类角色、物料策略、库存快照、BOM 快照、经营计划、生产日历和业务决策 checksum。
2. 每个节点执行：先扣该节点 GOOD 库存，再按 `childQty / parentQty` 把净需求传递给下层；保存 gross、inventoryBefore、inventoryUsed、inventoryAfter、net、path、终止原因。
3. `STOP` 立即形成末级需求；`EXPLODE` 且有子项才下钻；`EXPLODE` 无有效子项、缺策略、缺角色、缺供应方式、EXCLUDED、BOM 循环、多来源库存不足全部生成阻断 issue。
4. 循环检测按当前路径上的物料或边集合实现，不得把深度作为循环判定条件；循环必须失败并写入 `BOM_CYCLE`。
5. 根整机和中间节点库存都必须按业务分配结果扣减，不能用遍历顺序替代共享库存决策；没有库存记录不得静默按 0，必须生成 `INVENTORY_MISSING`，由业务确认。
6. 增加黄金示例 A=1000、整机库存100、B库存200、C库存300、D库存200，验证 B=700、C=1500、D=500；增加 STOP、EXPLODE 无 BOM、循环、共享库存和无记录测试。

### P0-004：BOM 快照没有落实最高有效版本和冲突阻断

**证据**：`BomSnapshotService.loadEdgesFromSource()` 使用 `SELECT DISTINCT`、`is_default` 和 `effective_date <= ?`，没有按“库存组织 + 父料号 + 子料号”取最高有效版本，也没有检测最高版本重复、有效期重叠或组织不明确。当前快照可以直接标记 READY。

**影响**：同一业务键的多版本可能同时进入计算；BOM 快照不是不可变、可解释的唯一真源。

**必须修复**：

1. NC 同步阶段先按 `inventory_org + parent_code + child_code` 选最高有效版本，保存父料号、子料号、库存组织、版本、有效期、数量。
2. 同一业务键出现多个最高版本、有效期重叠、库存组织为空或数量非法时，快照状态必须为 `FAILED/WAITING_DECISION`，写 `MULTIPLE_ACTIVE_BOM`、`BOM_VALIDITY_OVERLAP` 或 `BOM_ORG_MISSING`，不得 READY。
3. MRP 计算只读取已固化且 checksum 固定的 BOM snapshot，禁止计算时回查 `bom_detail`。
4. 快照 API 增加 validation 结果、错误详情和重复边查询；增加最高版本、组织隔离、重叠有效期和快照不可变测试。

### P0-005：批次生命周期仍是同步骨架，缺少锁定、任务、重算和修订

**证据**：`precheck` 只把状态改为 `CHECKING`；`lock-and-calculate` 只检查批次中的旧 `unresolvedCount`，没有重新预检和输入绑定；`calculate` 同步返回 200；没有 worker/task 查询和写入阶段；发布直接把原批次改为 `PUBLISHED`。

**影响**：后台计算可能混用最新数据；失败可能留下半成品；无法实现月中只重算未来未锁定日，也无法保留历史结果和生成发布修订版本。

**必须修复**：

1. 创建批次显式绑定经营计划、库存、BOM、角色、策略、生产日历和决策版本；写入 input checksum。
2. 预检必须真实生成全部 issue，状态按 `ANALYZING -> WAITING_DECISION/READY_TO_CALCULATE` 转换；没有人工决策不得计算。
3. 计算、日分配、写入使用异步 `calc_task`/worker，API 返回 taskId；采用短事务 Writer、幂等键、lease、重试、失败清理和 active run 切换。
4. 已执行或已锁定日期禁止重算、人工重分配和修订覆盖；月中重算只处理未来未锁定日期并生成新的 `mrp_calc_run`，旧 run 保留。
5. 增加执行确认 API/表，实际量由业务手动确认；实际量与当时有效计划量不一致生成 `EXECUTION_QUANTITY_MISMATCH`。
6. 发布后只能 `POST /mrp/plans/{id}/revisions` 或等价批次修订接口，记录 `revisionOfBatchId/revisionNo`，旧发布批次只读。
7. 月中库存分配“保留原分配/重新分配”未决前，只能生成差异预览并阻断发布，不得默认选择。

### P0-006：日排产 API 和算法不满足唯一日事实契约

**证据**：日排产接口返回整表列表，没有服务端分页、日网格、周汇总、月汇总、rowKey、effectiveQuantity 或 active run 过滤；`DailyDistributionEngine` 的分组键只含末级物料/类型/供应方式，不含来源整机、来源月份和路径，可能造成不同来源需求的 `demandKey` 冲突。分配使用固定 6 位向下取整后按整数 `1` 回补，不适用于小数物料；未生产目标日没有可用的业务解决接口。

**必须修复**：

1. 日事实唯一键至少包含工厂、来源整机、末级物料、终点类型、供应方式、`pathGroupKey`、排产日；主表 `rowKey` 使用契约定义的 SHA-256。
2. 月需求按有效生产日分配并保证来源月守恒；小数数量按物料精度量化，残差按稳定日期顺序回补。
3. `scheduleDate = sourceDate - 7 calendar days`；目标为非生产日写待分配，业务解决后才能落到合法日；不得自动平移。
4. 增加日网格、周汇总、月汇总、需求追溯、库存扣减 API；周/月只读聚合日事实，前端不得自行计算正式结果。
5. API 全部分页，支持工厂、来源整机、末级物料、终点类型、供应方式、人工/异常筛选；只读取 active READY/PUBLISHED run。
6. checksum 必须包含所有绑定输入、规则版本和 decision checksum，而不仅是日计划结果。

### P0-007：权限和敏感配置不符合安全契约

**证据**：`SecurityConfig` 只对 `/api/v1/mrp/**` 走通用 authenticated，没有 `mrp_*`、`daily_plan:*`、生产日历、决策和库存分配细粒度权限，也没有工厂数据域校验；`application.yml` 和 `backend/.env.example` 含真实格式的 Feishu app secret 默认值。

**必须修复**：

1. 删除仓库中的真实 secret 和默认 secret；仅从受控环境变量/密钥服务读取，轮换已经暴露的凭据。
2. 按契约增加后端 `@PreAuthorize`/数据域校验，区分查看、创建、预检、计算、决策、库存分配、日调整、发布、配置发布和导出。
3. 前端 route/button 权限只作体验控制，后端必须再次拒绝越权和越工厂访问；增加 401、403、跨工厂和无权限测试。

### P1-001：前端不是可验收的 Phase 2 工作台

**证据**：当前只有批次列表和批次详情两个简单页面；批次列表无服务端分页、搜索和完整筛选；详情没有业务决策、库存分配、生产日历、日网格、周汇总、月汇总、执行确认、修订和版本差异工作台；日排产表仍同时展示“来源日”和“排产日”，且没有“来源整机”列。API 类型和计算参数使用 `number`，与 DECIMAL 字符串契约冲突。

**必须修复**：

1. 按 `docs/MRP日排产页面设计方案.md` 实现日排产主页面：来源整机、末级需求物料、终点/供应、工厂为固定列，日期列按自然日；周/月只是只读汇总。
2. 增加业务决策中心、库存分配、生产日历、执行确认、修订/版本对比页面或工作台入口。
3. 使用服务端分页、搜索、筛选、加载态、空态和错误态；大于 28 天使用虚拟表格；不要一次加载全部日计划。
4. 所有正式数量使用 `DecimalString`，编辑使用文本校验，不得用 `number`、`el-input-number` 或前端浮点聚合。
5. 页面状态文案中文化，显示 `WAITING_DECISION/STALE/LOCKED/EXECUTED` 等阻断状态和原因；禁止只用颜色表达状态。
6. 依据原型和 `assets/import-design-rules.md` 补齐布局、导入模板、错误报告、弹窗和权限按钮；增加 Playwright 在 1440x900、1920x1080 和 84 日滚动下的截图/交互证据。

### P1-002：自动化测试和报告不可信

**必须修复**：

- 新增 Phase 2 引擎、BOM、库存、日分配、批次状态、执行锁定、修订和权限测试；不能只运行 Phase 1 测试；
- `BomExplosionServiceTest` 不能以 Docker 不可用为理由在正式门禁中跳过；应提供可复现的 MySQL/Testcontainers 环境或明确独立阻断；
- 增加 API 契约测试、迁移空库/升级测试和 Playwright E2E；
- 重写 10–19 报告，报告必须引用本次实际命令、退出码、数据库版本和测试数量；旧报告不得继续写 PASS；
- 清理新增 trailing whitespace，`git diff --check` 必须通过。

### P1-003：UAT 脚本和前端运行环境不能作为验收入口

**证据**：`deploy/run-uat-tests.sh`、`deploy/test-docker-acceptance.sh` 含 `exit1`、`sleep2`、`-eq30` 等错误命令；空库脚本执行 `docker compose down -v`，会删除共享 compose volume，违反验收安全要求；脚本仍使用旧 Phase 1 接口和固定本机绝对路径。前端 `.nvmrc` 要求 Node 20，但当前环境是 Node 23.11.0；Playwright 配置硬编码本机 Chromium 路径。

**必须修复**：

1. 先由业务/环境负责人手工准备契约要求的 Node 版本；代理不得修改 `.nvmrc`、`package.json` 或安装/切换 Node。
2. UAT 使用独立 compose project、独立 volume 和可配置 `BASE_URL/DB`；禁止对共享环境执行 `down -v`。
3. 修正 shell 命令、退出码和错误处理；脚本必须在 `set -euo pipefail` 下通过 shellcheck 或等价静态检查。
4. UAT 只调用当前 Phase 2 `/api/v1/mrp/**` 契约，不能用旧 `/plans` 接口冒充 Phase 2 验收；凭据只能由环境变量提供。
5. Playwright 使用标准浏览器发现或环境变量配置路径；没有浏览器时按环境问题停止，不得写死开发机路径。

## 4. 推荐修复顺序与 Agent 文件所有权

由 Coordinator 先冻结契约和工作区基线，再并行分配互斥文件：

| Agent | 负责范围 | 独占文件边界 |
|---|---|---|
| Migration/Data | V021/V022 历史核对、V023+、空库/升级验证 | `backend/src/main/resources/db/migration/*`、迁移报告 |
| Inventory/BOM | 品质导入、库存规范化、BOM 最高版本和 validation | inventory/import/BOM service、mapper、DTO、相关测试 |
| Calculation | 策略驱动净需求、库存分配、日分配、checksum | `backend/src/main/java/com/mrp/mrp/engine/**`、计算 service、测试 |
| Lifecycle/Security | 异步任务、锁定/执行/修订、权限、secret 清理 | controller、task/worker、SecurityConfig、运维配置、测试 |
| Frontend | 日网格、周/月汇总、决策/库存/日历/执行/修订工作台 | `frontend/src/views/mrp/**`、`frontend/src/api/mrp.ts`、路由和 E2E |
| Independent Reviewer | 只读复核上述所有变更 | 不直接与实现 Agent 共享写文件 |

执行顺序：先解决 P0-001 迁移门禁，再并行实现 P0-002/P0-003/P0-004；随后完成 P0-005/P0-006/P0-007；最后完成前端、专项测试、真实联调和最终审计。任一 P0 失败都不得进入发布验收。

## 5. 修复后强制验证

```bash
# 1. 代码质量
git diff --check

# 2. 后端
cd backend
./mvnw clean compile
./mvnw test

# 3. 前端
cd ../frontend
npm ci
npm run build
npm test -- --run
npx vue-tsc --noEmit

# 4. 数据库迁移
docker compose -f ../deploy/docker-compose.yml up -d mysql redis
# 在独立空库执行 V001 到最新迁移，并在现有数据库副本执行升级
flyway validate
flyway migrate

# 5. 真实服务联调
cd ../backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# 另一个终端至少验证：health、登录、无 token 401、无权限 403、批次创建、预检、决策、库存品质导入、BOM validation、异步计算、日网格、周汇总、月汇总、执行确认、修订和发布阻断

# 6. 浏览器/E2E
cd ../frontend
npx playwright test
```

验收必须保存：命令和退出码、Flyway history、空库和升级日志、API 请求/响应、浏览器截图、Playwright 报告、黄金示例计算明细和失败场景证据。

## 6. 最终签收标准

- 迁移可在空库和现有库升级，Flyway validate 通过；
- 库存 `GOOD/DEFECTIVE` 全链路正确，单位使用 BigDecimal；
- BOM 按组织/父/子唯一业务键固化最高有效版本，冲突阻断；
- A/B/C/D 黄金示例、STOP/EXPLODE、循环、共享库存和缺库存决策通过；
- 日事实守恒，提前 7 天、跨月、节假日、五周月和非生产目标日通过；
- 已锁定/已执行日不可覆盖，执行差异异常可追溯；
- 月中重算新 run，发布后新修订批次，旧结果只读；
- 日网格、周汇总、月汇总、需求追溯、库存扣减和业务工作台真实可用；
- 权限、工厂数据域、幂等、并发、事务和 secret 检查通过；
- 后端全量测试、前端测试、Playwright、真实 API/浏览器和 Docker UAT 全部通过；
- 独立 Reviewer `PASS`，workflow 10–19 和 FINAL-AUDIT 才能置 `DONE`。
