# MRP 多层净需求与日排产开发手册

> 2026-10-19 更新：新建日排产以文末 C022（v5，按实际业务周数均分并整体提前一业务周）为准；下文 v4 固定三份及更早减7天规则仅适用于对应历史版本。
**用途**：交给 Mimo 或其他编码代理自动执行 Phase 2。
**业务基线**：[MRP需求文档v4.0（Phase 2）.md](<MRP需求文档v4.0（Phase 2）.md>)
**执行入口**：根目录 `project.md`、`.harness/workflow.yaml`、`.harness/prompts/10-contract-migration.md`。
**重要声明**：本手册定义开发和验证方法，不表示相关代码已经完成。

## 1. 启动指令

将以下内容直接交给 Mimo：

```text
你负责完整实现 MRP Phase 2 多层净需求与日排产，必须使用仓库内 Harness 和本开发手册管理执行。

开始前完整读取：
1. AGENTS.md
2. project.md
3. .harness/execution-laws.md
4. .harness/README.md
5. .harness/source-precedence.md
6. docs/MRP需求文档v4.0（Phase 2）.md
7. docs/MRP多层净需求与日排产开发手册.md
8. .harness/workflow.yaml
9. 当前阶段 Prompt 及其引用文件

先执行 .harness/prompts/10-contract-migration.md。阶段实现完成后必须进入 REVIEW，由独立审查 Agent 执行 REVIEW-STAGE；失败时执行 REPAIR-GATE 并回到同一 Reviewer 复核。阶段 PASS 后自动进入下一阶段，直到 19 完成。遇业务未决、环境不满足、破坏性迁移或权限不足立即停止，不得猜测。

使用多 Agent，但必须先由 Coordinator 划定文件所有权。同一文件、迁移序号和共享契约同一时间只能有一个写入者。保留所有既有未提交改动，禁止 reset、checkout、清理或覆盖用户文件。

启动时必须枚举当前运行时是否具备 Agent spawn、独立 Reviewer 和并行任务能力。能力存在时按下表并行；能力不存在时不得把同一会话自审伪装成独立审查，需记录降级模式并要求另一会话/人工 Reviewer，若无法提供则在实现阶段 BLOCKED。

完成全部阶段后：先枚举运行环境是否真实提供 Superpowers 技能/CLI。若存在，记录能力名、版本和真实调用证据后执行；若不存在，不得伪造命令，按仓库现有 SDD 语义执行 REVIEW-STAGE -> REPAIR-GATE -> 再复核 -> FINAL-AUDIT，并把证据写入 .superpowers/sdd/2026-09-22-phase2-daily-mrp/。

最终必须完成编译、单元/集成测试、MySQL+Redis+后端实际启动及 curl、前端构建和浏览器验证、Playwright、Docker 空库与升级 UAT。发现失败要根因分析、最小修复、定向复测、全量回归和独立复核，阻断清零前不得报告完成。
```

## 2. 执行前真实现状

开始开发前重新验收代码，不得只依赖本节。2026-09-22 审计发现：

| 范围 | 当前实现 | Phase 2 要求 |
|---|---|---|
| 算法 | `PlanningCalculator` 使用 `long`，月量拆 W1-W3，W4 取下月 W1并把下月 W1 置 0 | 新增 BigDecimal 多层净需求和日分配引擎，不修补旧周算法 |
| 已出货 | 参与 `forecast-inventory-shipped` 和 checksum | 本期完全忽略，标记 `TODO-SHIPMENT` |
| BOM | 按 UI 传入分类停止；只乘 `childQty` | 按物料策略 `STOP/EXPLODE`；用 `childQty/parentQty` |
| 库存 | `inventory_item` 与 `inventory_detail` 双路径，计算只读后者 | 原始导入与规范化计算真源分层；新建快照组+仓库范围；按仓库+物料+库存品质汇总，工厂由已发布映射确定，禁止双真源相加 |
| 库存扣减 | BOM 试验路径对子件错误扣根整机库存 | 每个节点扣本物料库存，净需求再下传 |
| 数量 | 周计划和库存多处 `long`，存在 `.longValue()` | 新链路统一 `BigDecimal` / `DECIMAL(18,6)` |
| BOM 版本 | 当前表是可变同步态 | 创建不可变 `bom_snapshot` 并绑定批次 |
| 计划事实 | `plan_detail`/`plan_override` 是周粒度 | 新增日事实与日覆盖表，旧表只读兼容 |
| 覆盖键 | 版本+物料+周 | 工厂+来源整机+末级物料+日期+必要路径键 |
| 前端分组 | 仅物料+工厂，可能合并不同来源整机 | 行键必须包含来源整机与末级物料 |
| 事务 | 同类调用 `@Transactional writePlanVersion()` | 独立 Writer Bean 或 `TransactionTemplate` |
| 任务 | 主要为 PENDING/SUCCEEDED/FAILED | 增加预检、等待决策、锁定、计算、过期状态 |
| 原型 | 周网格和来源月份语义 | 更新为日事实页面；旧原型只参考布局和视觉 |

关键验收文件：

```text
backend/src/main/java/com/mrp/planning/domain/PlanningCalculator.java
backend/src/main/java/com/mrp/planning/service/PlanningService.java
backend/src/main/java/com/mrp/bom/service/BomExplosionService.java
backend/src/main/java/com/mrp/planning/service/PlanningDomainService.java
backend/src/main/resources/db/migration/
backend/src/main/resources/mapper/
frontend/src/views/planning/PlanningGrid.vue
frontend/src/api/types.ts
frontend/src/router/index.ts
frontend/src/App.vue
```

## 3. 不可变业务规则

实现过程中不得自行改变：

1. 经营计划仍是整机月度输入。
2. 已出货本期不参与任何结果、校验、checksum 或发布门禁。
3. 每层先扣当前节点库存，只把净需求传给下层。
4. 同一库存快照池跨整机、来源月份和路径只消费一次，不能按月份重复初始化。
5. `unitUsage = childQty / parentQty`。
6. 物料是否终止由版本化 `explosion_policy` 决定；外购/委外不自动终止。
7. 缺角色、缺策略、缺库存行和非生产日落点由业务决策；共享库存按来源月+经营计划冻结顺序自动分配，人工改动才进入决策和审计。
8. 计算开始前决策必须完成并锁定，后台计算不弹窗。
9. 日排产是唯一事实，周/月只读聚合。
10. 月需求按当月有效生产日分配，所有日需求提前 7 个自然日。
11. 提前后落非生产日进入待分配池，不自动前移或后移。
12. 页面固定展示“来源整机”“末级需求物料”，第二列不得称“原材料”。
13. 主页面只展示最终排产日期，不展示“来源需求周/实际排产周”。
14. 所有数量使用 BigDecimal；整数分摊用最大余数法并保证守恒。
15. 相同锁定输入和决策必须产生相同稳定 checksum。
16. Phase 1 表、API 和历史版本保持只读兼容，不能破坏性改造。
17. 库存模板、解析、预览和规范化必须携带 `inventory_quality`：`GOOD` 参与 MRP，`DEFECTIVE` 只展示和追溯；仓库在快照范围头选择，工厂通过已发布的工厂仓库映射确定，不按库存明细行重映射。
18. 经营计划导入顺序是共享库存优先级真源；必须持久化 `source_row_no/priority_seq`，同来源月内按该顺序分配，不得依赖 `HashMap`、料号或前端排序。
19. 库存按仓库范围隔离；工厂通过已发布一对一映射只扣减指定仓库，初始覆盖永惠、爱培科、惠州仓。
20. 安全库存是库存池 `reserved_qty` 的硬保底，可分配量为 `MAX(0, GOOD-safety)`；预警库存产生风险投影，不阻止继续生成需求缺口。
21. 业务按月发布 W1-Wn 日期范围；周汇总从日事实按该范围聚合，不写死周一至周日，不恢复可编辑周事实。
22. NC 同步按库存组织+父料号+子料号选最高有效版本；重复最高版本、有效期重叠或组织不明必须阻断。
23. MVP 不做计量单位换算，按现有数字和 `childQty/parentQty` 计算。
24. 已执行/已锁定日不可重算覆盖；月中只重算未来未锁定日；发布后修改生成修订批次。

上述经营计划优先级、仓库隔离、安全/预警库存和业务周的完整数据模型、API、页面、事务和黄金案例见 `docs/MRP Phase2补充设计-经营计划优先级与仓库安全库存自定义周.md`。

## 4. 目标架构

### 4.1 模块

```text
masterdata
  category-role version
  material-policy version
  production-calendar version

bom
  current sync tables
  immutable snapshot builder

inventory
  raw import/staging
  mrp_inventory_balance normalized calculation truth
  allocation pools

mrp
  precheck and decisions
  net requirement engine
  daily distribution engine
  orchestration and result writer
  daily/weekly/monthly queries

planning
  Phase 1 legacy read compatibility
  Phase 2 publish/override/export facade
```

不要把新算法塞回现有 `PlanningCalculator` 或把新状态全部塞进旧 `calc_task`。优先新增清晰的 Phase 2 领域包与适配层，旧代码保留历史读取。

### 4.2 服务边界

| 服务 | 职责 | 事务边界 |
|---|---|---|
| `MrpConfigurationService` | 分类角色、物料策略、生产日历草稿/发布 | 每次配置写入短事务 |
| `BomSnapshotService` | 从当前 BOM 固化、校验、计算 checksum | 快照头+明细原子提交 |
| `InventoryNormalizationService` | 原始库存转换为唯一计算快照 | 确认导入原子提交 |
| `MrpPrecheckService` | 扫描所有阻断项并创建 issue | 批次+issue 短事务 |
| `DecisionService` | 单条/批量业务决策、版本化和审计 | 乐观锁短事务 |
| `InventoryAllocationService` | 共享库存预览、确认、守恒 | 池级短事务+revision |
| `NetRequirementExplosionEngine` | 纯 Java 多层净需求 | 无数据库事务 |
| `DailyDistributionEngine` | 生产日分摊和提前 7 天 | 无数据库事务 |
| `MrpCalculationOrchestrator` | 锁定输入、Worker 执行、心跳、取消 | 不持有长事务 |
| `MrpResultWriter` | 批量写结果和原子置 READY | 独立 Bean 最终短事务 |
| `MrpPublishService` | 发布门禁和审计 | 单次短事务 |
| `MrpQueryService` | 日事实、周/月汇总和追溯 | 只读 |

## 5. 数据库设计

所有迁移只新增，开始阶段先扫描最高版本；当前观察到 V020，预计从 V021 开始，但必须以执行时工作树为准。

### 5.1 版本和配置

```text
mrp_category_role_version
  id, version_no, status, checksum, created_by, created_at, published_at

mrp_category_role_detail
  version_id, category_code, role, enabled, revision
  UNIQUE(version_id, category_code)

mrp_material_policy_version
  id, version_no, status, checksum, created_by, created_at, published_at

mrp_material_policy_detail
  version_id, material_code, supply_mode, explosion_policy,
  quantity_scale, revision
  UNIQUE(version_id, material_code)

production_calendar_version
  id, version_no, factory_code, status, checksum, ...

production_calendar_day
  version_id, factory_code, calendar_date, is_production_day,
  allocation_weight DECIMAL(18,6), daily_capacity DECIMAL(18,6) NULL,
  reason, revision
  UNIQUE(version_id, factory_code, calendar_date)
```

### 5.2 快照和批次

```text
bom_snapshot
  id, source_version, status, checksum, effective_at, created_at

bom_snapshot_detail
  snapshot_id, parent_code, child_code,
  parent_qty DECIMAL(18,6), child_qty DECIMAL(18,6),
  bom_version, effective_date, organization_code, body_scope, path_metadata
  UNIQUE(snapshot_id, organization_code, body_scope, parent_code, bom_version, child_code)

mrp_inventory_balance
  inventory_snapshot_id, factory_code, inventory_org,
  material_code, inventory_quality GOOD|DEFECTIVE,
  available_quantity DECIMAL(18,6), source_row_ref,
  included_flag, normalization_rule_version
  UNIQUE(inventory_snapshot_id, factory_code, inventory_org, material_code, inventory_quality)

mrp_calc_batch
  id, factory_code, status, forecast_version_id, inventory_snapshot_id,
  bom_snapshot_id, category_role_version_id, material_policy_version_id,
  production_calendar_version_id, decision_checksum, rule_version,
  input_checksum, result_checksum, version_no, active_run_id,
  published_at, active_request_key, issue_count, unresolved_count,
  revision_of_batch_id, revision_no, superseded_by_batch_id,
  version, created_by, timestamps

mrp_calc_run
  id(run_id), batch_id, status, result_checksum, row_counts_json,
  started_at, finished_at, error_code, error_message

mrp_daily_execution
  batch_id, row_key, schedule_date, execution_status,
  actual_quantity, confirmed_by, confirmed_at, remark, revision
```

`shipment_batch_id` 可保留为 nullable 追溯字段，但本期必须为 null 或不进入任何计算/checksum。

### 5.3 决策和库存分配

```text
mrp_decision_issue
  batch_id, issue_key, issue_type, severity, scope_type, scope_key,
  payload_json, status, revision, created_at, resolved_at
  UNIQUE(batch_id, issue_key)

mrp_decision_record
  issue_id, decision_code, decision_payload, reason,
  operator_id, expected_revision, decision_checksum, created_at

mrp_inventory_pool
  batch_id, snapshot_id, factory_code, inventory_org, material_code,
  available_qty, reserved_qty, allocated_qty, version

mrp_inventory_allocation
  pool_id, demand_key, root_material_code, source_month, path_hash,
  gross_qty, allocated_qty, manual_confirmed, revision
  UNIQUE(pool_id, demand_key)
```

### 5.4 计算结果

```text
mrp_requirement_detail
  run_id, batch_id, factory_code, root_material_code, source_month,
  parent_material_code, current_material_code, level_no,
  path_text, path_hash, category_role, supply_mode, explosion_policy,
  gross_qty, inventory_before_qty, inventory_used_qty,
  inventory_after_qty, net_qty, terminal_flag, terminal_reason
  UNIQUE(run_id, factory_code, root_material_code, source_month, path_hash, current_material_code, level_no)

mrp_terminal_demand
  run_id, batch_id, factory_code, root_material_code, terminal_material_code,
  terminal_type, supply_mode, source_month, quantity
  UNIQUE(run_id, factory_code, root_material_code, terminal_material_code, terminal_type, supply_mode, source_month)

mrp_daily_plan_detail
  run_id, batch_id, factory_code, root_material_code,
  terminal_material_code, terminal_type, supply_mode, path_group_key,
  source_month, source_date, schedule_date,
  system_quantity,
  is_production_day, revision
  UNIQUE(run_id, factory_code, root_material_code, terminal_material_code, terminal_type, supply_mode, path_group_key, schedule_date)

mrp_daily_plan_override
  batch_id, factory_code, root_material_code,
  terminal_material_code, terminal_type, supply_mode, path_group_key, schedule_date,
  manual_quantity, reason, revision, operator_id, created_at
  UNIQUE(batch_id, factory_code, root_material_code, terminal_material_code, terminal_type, supply_mode, path_group_key, schedule_date)

mrp_unallocated_quantity
  run_id, batch_id, demand_key, source_date, attempted_schedule_date,
  quantity, reason_code, status, resolved_schedule_date, revision
  UNIQUE(run_id, demand_key, source_date, attempted_schedule_date)

mrp_calc_exception
  run_id, batch_id, severity, error_code, scope_key, message, detail_json
  INDEX(run_id, severity, error_code, scope_key)
```

新表数量列统一 `DECIMAL(18,6)`。日自动事实只存 `system_quantity`，人工值存当前有效 override，API/汇总用 `COALESCE(manual_quantity, system_quantity)` 形成 effective quantity；不得把三个字段变成可独立写入的多真源。索引至少覆盖批次状态认领、版本外键、日网格行键+日期、周汇总日期范围、issue 状态、库存池键和追溯路径。迁移中加入唯一约束、非负 CHECK（MySQL 8）和必要外键；大表外键/索引对写入性能的影响需用真实数据验证。

## 6. 算法实现

### 6.1 预检

预检负责版本、结构和规范化输入检查；共享库存和深层需求可能只有在展开后才能确定，因此采用可重复的分析波次，不要求一次预检猜出全部问题：

```text
校验输入版本状态
校验经营计划物料为 FINISHED_GOOD
校验分类角色完整
校验供应方式和下钻策略完整
校验 BOM 快照完整、无循环、数量合法、有效版本唯一
校验所有经过节点存在库存记录
校验生产日历覆盖来源月份及提前后的目标范围
生成基础 issue 并把批次置 ANALYZING
```

每一轮分析按来源月+经营计划优先级展开当前可计算节点，生成共享库存需求并自动形成 `AUTO_PRIORITY` 分配，同时发现深层缺策略、缺库存策略、EXPLODE 无 BOM 和非生产日落点 issue。发现需要业务决定或存在人工分配改动则进入 `WAITING_DECISION`。决策后进入 `REANALYZING`，直到 issue 和分配稳定后锁定 decision checksum。同一输入重复分析使用稳定 `issue_key`，不得生成重复问题。

### 6.2 净需求引擎

输入使用不可变 DTO，一次性批量加载到内存图；引擎不能调用 Mapper。推荐 BFS 或显式栈 DFS，并设置最大深度和最大节点数。

伪代码：

```text
queue <- root(forecast)
while queue not empty:
  node <- queue.pop(stableOrder)
  assert role and policy maintained
  gross <- node.gross
  allocation <- lockedAllocation(node.demandKey)
  net <- max(0, gross - allocation)
  record requirement detail

  if policy == STOP:
    emit terminal(node, net)
  else if children is empty:
    emit blocking issue EXPLODE_WITHOUT_BOM
  else:
    for child sorted by childCode + bomVersion:
      ratio <- childQty / parentQty
      queue.add(child, net * ratio)
```

库存不是每条路径各自复制。先按仓库+物料建立池，从物理良品库存中扣除安全库存得到可分配量，再按 `source_month ASC, priority_seq ASC, root_material_code ASC, current_material_code ASC, demand_key ASC` 自动依次分配。系统结果标记为 `AUTO_PRIORITY`；业务只在需要改变默认优先级时通过工作台提交 `MANUAL_OVERRIDE`，填写原因并校验 revision。不允许使用隐式遍历顺序先来先得。

### 6.3 日分配引擎

```text
weightTotal = SUM(valid production-day weights in source month)
rawDayQty = monthlyTerminalQty * dayWeight / weightTotal
sourceDate = production day
scheduleDate = sourceDate.minusDays(7)
```

整数物料最大余数法：先向下取整，再按小数余数降序、日期升序补足差额。小数物料按 `quantity_scale` 量化并按稳定顺序补差。目标日期非生产日不写入日事实，写 `mrp_unallocated_quantity` 和 issue。

### 6.4 稳定 checksum

使用明确字符集和加密散列（例如 SHA-256），输入字段按固定顺序序列化：

```text
ruleVersion
factory
forecastVersion/checksum
inventoryVersion/checksum
bomSnapshot/checksum
roleVersion/checksum
policyVersion/checksum
calendarVersion/checksum
decisionChecksum
```

结果按 `factory, root, terminal, pathGroupKey, scheduleDate` 排序后散列。禁止使用 Java `String.hashCode()`。

## 7. API 契约摘要

所有接口使用 `/api/v1`、统一错误体、服务端分页、工厂数据域、权限码、幂等键和乐观锁。

```text
配置版本
GET/POST /mrp/category-role-versions
PUT       /mrp/category-role-versions/{id}/details
POST      /mrp/category-role-versions/{id}/publish
GET/POST /mrp/material-policy-versions
PUT       /mrp/material-policy-versions/{id}/details
POST      /mrp/material-policy-versions/{id}/publish
GET/POST /production-calendar-versions
PUT       /production-calendar-versions/{id}/days
POST      /production-calendar-versions/{id}/publish

BOM 快照
POST /bom-snapshots
GET  /bom-snapshots/{id}
GET  /bom-snapshots/{id}/validation

计算批次
POST /mrp/batches
POST /mrp/batches/{id}/precheck
GET  /mrp/batches/{id}
GET  /mrp/batches/{id}/issues
PUT  /mrp/batches/{id}/issues/{issueId}/decision
POST /mrp/batches/{id}/issues/batch-decisions
POST /mrp/batches/{id}/lock-and-calculate
GET  /mrp/tasks/{taskId}

库存分配
GET  /mrp/batches/{id}/inventory-pools
GET  /mrp/batches/{id}/inventory-pools/{poolId}/demands
PUT  /mrp/batches/{id}/inventory-pools/{poolId}/allocations
POST /mrp/batches/{id}/inventory-pools/{poolId}/confirm

结果与调整
GET    /mrp/plans/{id}/daily-grid
GET    /mrp/plans/{id}/weekly-summary
GET    /mrp/plans/{id}/monthly-summary
GET    /mrp/plans/{id}/requirement-traces
GET    /mrp/plans/{id}/inventory-allocations
POST   /mrp/plans/{id}/daily-reallocations
DELETE /mrp/plans/{id}/daily-overrides/{overrideId}
POST   /mrp/plans/{id}/publish
```

`/mrp/plans/{id}` 的 `{id}` 就是 `mrp_calc_batch.id`；MVP 不提供可绕过守恒的单日 PUT，只通过一次多日重分配提交同一行的全部变更。

日网格请求必须包含日期范围、分页与筛选；响应数量用 decimal string 避免 JavaScript 精度丢失，并返回有序 `calendarDays`、`weekGroups` 和稳定 `rowKey`。

## 8. 前端实施

前端开始前必须先更新并评审新原型，再读 `prototype/`、`assets/import-design-rules.md`、`.harness/list-rules.md`。使用 Vue 3、TypeScript、Element Plus 和 fetch，不用 `any` 绕过类型。

### 8.1 信息架构

```text
计划
  MRP 计算批次
  业务决策中心
  库存分配
  日排产
  周汇总
  月汇总
  版本记录

数据
  经营计划
  库存快照
  物料
  物料分类
  BOM

配置
  物料分类角色
  物料 MRP 属性
  生产日历
  工厂仓库映射
  库存控制策略
  业务周期间
```

2026-10-14 日产能改版：当前入口移除独立周产能配置，原 `/capacity` 跳转生产日历。用户更正后新批次使用 `v3-whole-machine-capacity`：从level0 netQty生成独立MACHINE_OUTPUT整机日事实，整数台等权初排；最终日期仍减7天，冻结日产能台/日仅与整机effectiveQuantity比较。末级备料视图和周/月汇总与整机隔离，历史v2口径保留；整机人工跨日整数守恒调整。旧周产能表/API仅用于历史兼容。

### 8.2 日排产网格

- 固定列：“来源整机”“末级需求物料”“终点/供应”“工厂”；
- 默认 28 天，日期按已发布业务周 W1-Wn 分组；12 周使用虚拟列/虚拟滚动；
- 服务端分页，不把所有工厂和物料拉到前端分组；
- 搜索分别支持来源整机和末级物料；
- `rowKey` 驱动详情和编辑，禁止使用 `find(materialId + date)`；
- 单元格显示最终数量，点击打开日调整抽屉；多日重分配使用独立弹窗；
- 详情包含路径、逐层扣减、来源日和审计，但主网格不显示来源周；
- `STALE`、待决策、待分配、无日历覆盖时有明确阻断态；
- 空数据用 `el-empty`，所有请求有 loading、401/403/500 处理。

### 8.3 配置和工作台

- 分类角色、物料策略、日历均为草稿/发布/历史版本；
- 决策中心展示待处理、阻断、警告计数，支持单条、批量和导入；
- 库存分配展示库存池、来源需求、已分配、保留、差额和 revision；
- “按月份/比例”等按钮只能生成预览，必须业务确认后生效；
- 所有列表遵守服务端分页默认 20、最大 100。

## 9. 事务、并发与恢复

1. `POST /mrp/batches` 使用 `Idempotency-Key`，相同 active request 只生成一个批次。
2. 预检可重跑但 issue 幂等；不能把 `WAITING_DECISION` 当技术失败超时清理。
3. 决策和库存分配带 `expectedRevision`；冲突返回 409，不做最后写入覆盖。
4. 锁定操作在一个短事务内验证所有分析波次、issue、分配和版本 checksum，生成不可变 decision checksum；分析未稳定不得锁定。
5. Worker 通过 `FOR UPDATE SKIP LOCKED`、租约和心跳认领；同工厂同输入加 Redisson 业务锁。
6. 纯计算不持有数据库事务；阶段结果写任务隔离的 staging 或内存。
7. `MrpResultWriter` 先创建 `mrp_calc_run`，以 500-2000 行为单位在独立短事务写入隔离结果；写入期间批次为 `WRITING`，查询只读旧 active run。
8. Writer 完成后校验行数、逐层守恒、日历合法性和 checksum，再以真正短事务切换 `active_run_id` 并置 `READY`；失败 run 隔离，批次进入 `FAILED`，已发布版本保持不变。
9. 配置或输入更新触发未发布批次 `STALE`；旧决策/分配不得复制到不相同 checksum。
10. 取消只在安全 chunk 边界生效；最终原子提交阶段不可强杀。
11. 执行确认单独写入 `mrp_daily_execution`；`LOCKED/EXECUTED` 日不可被重算、重分配或修订覆盖。实际完成量与有效计划量不一致时写 `EXECUTION_QUANTITY_MISMATCH`。
12. 月中重算必须创建新 `mrp_calc_run`；旧 run 保留可审计。库存分配“保留原分配/重新分配”未定前，只能生成差异预览，不得发布受影响结果。
13. 已发布批次不可原地修改；修订通过新批次关联 `revision_of_batch_id`，新版本发布时原子记录 `superseded_by_batch_id`，旧版本只读保留。

## 10. 多 Agent 编排

Coordinator 是唯一工作流写入者，负责 `workflow.yaml`、`decision-log.md`、`issue-log.md`、阶段报告和最终合并。每阶段开始写文件所有权表，Agent 不得修改未分配文件。

推荐角色：

| Agent | 文件域 | 可并行条件 |
|---|---|---|
| A 契约与 Schema | 新迁移、领域实体、Mapper XML | 契约冻结后；迁移文件独占 |
| B 算法 | 纯 Java 净需求/日分配及单测 | DTO 契约冻结后，可与 A 并行 |
| C 主数据与决策 | 配置、预检、决策、库存分配 | Schema 可用后 |
| D API 与任务 | Controller、Orchestrator、Writer、任务状态 | 算法和 Schema 稳定后 |
| E 前端 | 新原型、API types、页面、E2E | API 契约冻结后 |
| F 验证 | 只读 Review、性能、安全、UAT | 每阶段 REVIEW 时独立执行 |

禁止并行自由修改：数据库迁移序号、共享 DTO、`PlanningService`、安全配置、路由、`workflow.yaml`。需要交叉时先由 Coordinator 合并契约，再顺序交接。

每个 Agent 必须在 `.superpowers/sdd/2026-09-22-phase2-daily-mrp/` 写 `task-N-brief.md` 和 `task-N-report.md`，Coordinator 维护 `progress.md`。目录是 SDD 证据，不代表存在 Superpowers 命令。

## 11. 阶段计划

| 阶段 | 目标 | 主要门禁 |
|---|---|---|
| 10 | 契约迁移、代码基线审计、新原型范围 | 文档无冲突、Phase 1 标为历史 |
| 11 | 新增数据库迁移和持久化骨架 | 空库迁移、V020 升级、旧 checksum 未变 |
| 12 | 分类角色、物料策略、日历、决策框架 | 版本发布不可变、权限/审计 |
| 13 | 库存导入规范化和库存池骨架 | 单一真源、GOOD/DEFECTIVE、缺行/零行区别、导入契约；不提前生成深层 demand |
| 14 | BOM 快照和多层净需求引擎 | 黄金测试、比例、STOP、循环、守恒 |
| 15 | 日分配、提前 7 天、待分配和汇总 | 跨月/节假日/最大余数、日周月守恒 |
| 16 | 批次 API、任务编排、Writer、发布 | 幂等、租约、409、回滚、STALE |
| 17 | 前端工作台和日排产页面 | 原型对照、服务端分页、浏览器闭环 |
| 18 | 集成、性能、安全、兼容 | 无 N+1、数据域、秘密扫描、旧版只读 |
| 19 | Docker 空库/升级、UAT、最终审计 | 实现审查 PASS 后置 DONE，再由独立 FINAL-AUDIT；失败可重开责任阶段 |

阶段状态：`TODO -> IN_PROGRESS -> REVIEW -> DONE`；同一时刻只能一个阶段 `IN_PROGRESS`。新工作流允许门禁 PASS 后自动继续；任何业务决策或环境问题停止。

## 12. 测试矩阵

### 12.1 纯领域

- A=1000、A库存100、B×1库存200、C×2库存300、B->D×1库存200，终点 C=1500、D=500；
- `parent_qty != 1`、小数用量、0/负数量、重复边、多有效 BOM；
- BOM 循环、最大深度、最大节点数、物料不存在；
- `STOP` 自制/外购/委外，`EXPLODE` 有/无 BOM，未维护策略；
- 缺库存确认 0 与明确 0 库存记录；
- GOOD/DEFECTIVE 同物料分行、快照+仓库+物料+品质规范化、工厂只扣减映射仓库且不混合品质；
- NC 同一库存组织+父料号+子料号重复最高版本、有效期重叠和组织缺失阻断；
- 不做单位换算，数字比例直接计算；
- 多整机共享库存按来源月+经营计划顺序分配、同物料多路径回分、分配守恒和失效；
- 永惠/爱培科/惠州仓隔离，工厂不得跨仓扣减；
- 安全库存硬保底、预警状态和配置变化失效；
- 已出货数据变化时结果和 checksum 不变。

### 12.2 日历与日计划

- 28/29/30/31 天、闰年、跨年、业务月有 W4/W5 且范围可跨自然月；
- 周末、法定节假日、调休日、整周停产、整月无生产日；
- 提前 7 天跨月/跨年；目标为非生产日进入待分配；
- 等权和非等权分配、整数最大余数、小数精度；
- 来源月合计、日合计、周汇总和月汇总守恒；
- 展示 12 周时多读取截止日后 7 天来源。

### 12.3 持久化与任务

- 所有版本绑定和 checksum；
- Writer 中途失败全回滚，无 READY 半版本；
- 幂等 request key、两个 Worker 并发认领、租约过期恢复；
- `WAITING_DECISION` 不被技术超时任务改为 FAILED；
- 乐观锁冲突 409；输入变化后批次和决策 `STALE`；
- 已发布版本不被重算覆盖。
- 已执行/已锁定日期不被重算覆盖；月中重算产生新 run；
- 锁定日实际量差异异常；发布后修订批次保留旧版本和审计。

### 12.4 API、安全和导入

- 202 长任务、合法状态迁移、统一错误体、分页上限；
- 401/403、权限码、工厂数据域、越权下载；
- Excel 表头+提示行+示例、rawRows/headers、稀疏列、BigDecimal、错误报告最后一列；
- 文件类型、大小、行数、宏、公式和恶意内容；
- 日志和审计不泄露 token、密钥和原文件内容。

### 12.5 前端与 E2E

- 来源整机/末级需求物料双列和稳定行键；
- 同末级物料来自两个整机时显示两行，编辑互不覆盖；
- 外购机头 `STOP` 时第二列显示机头和“机头·外购”；
- 日调整后周/月汇总同步，恢复自动值保留历史；
- 非生产日不可编辑，待决策和待分配禁止计算/发布；
- 空、加载、403、500、STALE、并发冲突；
- 1440x900、1920x1080 和必要移动视口，无文字溢出和重叠；
- 1 万行服务端分页、84 日虚拟滚动。
- 强闭环：创建批次 -> 分析波次 -> 处理缺配置/缺库存 -> 共享库存分配 -> 锁定 -> READY -> 校验日/周/月 -> 发布；断言每一步状态和精确 decimal 数量。
- 同一末级物料来自两个整机时只调整一行，另一行和汇总不变；非生产目标日先阻止发布，业务指定合法日期后发布成功；断言无控制台错误。

### 12.6 性能与迁移

- 使用可复现数据覆盖约 10 万根需求和真实 14-16 万级 BOM；
- 记录 CPU、内存、SQL 数、批量大小和耗时，首次压测后再冻结阈值；
- 证明无逐物料 N+1，批量写 500-2000；
- 校验 V001-V020 未改；空库迁到最新；从 V020 真实结构升级；重复启动安全。

## 13. 三层验证与修复闭环

每次代码改动：

```text
后端编译: cd backend && ./mvnw compile
后端测试: cd backend && ./mvnw test
前端构建: cd frontend && npm run build
前端测试: cd frontend && npm test
类型检查: cd frontend && npx vue-tsc --noEmit
```

每阶段完成后，纯领域单测可独立先跑；进入 Spring/Mapper/API/E2E 前必须先启动隔离 MySQL、Redis并验证健康、端口和迁移，再启动 API、Worker 和前端，使用 curl 验证健康、登录、新接口业务值、401/403、空/错场景；再用浏览器验证页面、Network、控制台和截图，最后执行 Playwright。不得以 H2 或 mock 代替唯一集成证据。

当前已知验证风险：

- `.nvmrc` 为 Node 20，而审计时当前 Node 是 23；正式编码前若仍不一致，按 `AGENTS.md` 环境问题格式停止，让用户手工切换；
- Playwright 配置存在本机浏览器绝对路径，应在不改变技术栈的前提下改为标准发现或可配置路径；若浏览器缺失属于环境问题，代理不能自行安装；
- 现有 Docker/UAT 脚本存在 `exit1`、`sleep2` 和 `down -v` 风险，不能直接运行；先修复脚本并使用独立 compose project/端口/卷；
- `package.json` 当前没有 lint script，不得虚报 `npm run lint` 通过；按阶段决策补充规范 lint 或记录 `vue-tsc + build + test` 的等价门禁；
- 配置中存在 secret-like 默认值，安全阶段必须移除默认密钥并要求环境变量，报告不得复述秘密值。

失败闭环：

```text
保存命令、exit code、日志、截图/trace
定位根因并分类（新功能、新类型/lint、既有债务、规则冲突）
写最小修复
先跑失败用例
跑受影响模块回归
跑全量三层验证
由原独立 Reviewer 复核
PASS 才更新阶段 DONE
```

禁止删除测试、降低断言、修改技术栈、清理主数据卷、绕过发布门禁或把失败标为完成。同一根因连续三轮仍无法推进才按 Harness 记录 `BLOCKED` 并请求用户决策。

## 14. Superpowers/SDD 处理

当前仓库只有 `.superpowers/sdd/` 历史 brief/report/progress 文件，没有已验证的 Superpowers 技能、CLI、manifest 或脚本。因此：

1. 执行环境若能真实枚举 Superpowers，记录名称、版本、命令和输出后使用；
2. 若不能枚举，不得编造 `superpowers` 命令；
3. 使用仓库已存在的 Harness 复核链路完成等价闭环；
4. SDD 证据目录保存每个 Agent 的任务 brief、报告、测试证据和进度台账；
5. 最终 `FINAL-AUDIT` 从“需求 -> 代码 -> API/UI -> 自动化测试 -> 运行证据”逐项核验。

## 15. 完成定义

只有以下全部满足，才能报告 Phase 2 完成：

- v4.0 每条已确认规则都有代码、测试和运行证据；
- 所有阶段经独立 Reviewer PASS，阻断问题为 0；
- 数据库迁移、净需求、库存分配、日排产、汇总、决策、调整和发布形成完整闭环；
- 已出货确实不影响本期结果，并保留明确 TODO；
- Phase 1 历史只读兼容；
- 后端/前端全量测试、真实 API、浏览器、Playwright、Docker 空库和升级 UAT 通过；
- 无高危权限、密钥、事务、并发或数据守恒缺陷；
- `.harness/reports/`、`.superpowers/sdd/2026-09-22-phase2-daily-mrp/` 和 UAT 记录完整；
- 未发生用户文件覆盖、旧迁移修改或未经批准的技术栈/中间件变更。

## 2026-10-19 C022：按实际业务周数均分并整体提前一周（用户已确认）

新建计划采用 `v5-shifted-business-weeks`，替代新计划原有的固定三份规则。来源月有 N 个冻结业务周，则按 N 份均分月净需求；基本份额向下取整，全部周余数放第一份。第一份排到上月最后一个业务周，其余份额依次排到本月第1至第 N−1 个业务周。本月最后业务周承接下月第一份；末月没有后续需求时该周留空。不额外减7个自然日。

例：10月4个业务周、净需求1003，分为253/250/250/250，分别排到9月末周及10月前3周。11月若有5个业务周、净需求1003，则分为203/200/200/200/200，分别排到10月末周及11月前4周。周内按本厂生产日均分，日余数从早到晚分配。整机使用整数；末级小数保留6位精度，数量守恒。

日事实保留来源月，周按冻结业务周、月按实际生产自然月汇总。缺少业务周、业务周配置异常、生产日历不完整或整周停产时，对应数量进入待分配池。既有v4及更早批次（含其修订版）继续使用原规则；需使用新规则时新建计划。
