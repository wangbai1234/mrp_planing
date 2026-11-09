# MRP 数据与版本契约（Phase 2）

本文件约束当前 Phase 2 新模型。Phase 1 的 `plan_version/plan_detail/plan_override` 保持历史只读兼容，不得用改字段语义的方式承载日排产。

## 1. 公共字段

- 主键默认 `BIGINT`；对外稳定业务键使用明确 `VARCHAR`；
- Phase 2 数量统一 `DECIMAL(18,6) NOT NULL`，Java 使用 `BigDecimal`；
- 月份统一存每月 1 日，Java 领域使用 `YearMonth`；
- 日计划使用 `schedule_date DATE`；周是查询时按已发布 `planning_week_definition` 日期范围聚合的 W1-Wn，不写死周一至周日；
- 时间存 UTC `DATETIME(3)`，接口 ISO-8601，前端按 Asia/Shanghai 展示；
- 料号、分类、仓库、工厂和产线编码始终按文本；
- 版本输入和计算结果不可物理覆盖；发布后的配置版本不可原地修改；
- 所有列表表必须有适合稳定排序的唯一键和服务端分页索引；
- 所有可并发修改的草稿、issue、分配和覆盖记录必须有 `revision/version` 乐观锁字段。

## 2. 输入版本

`mrp_calc_batch` 是 Phase 2 唯一的计划/版本身份；对外 `planId = batchId`。不得为 Phase 2 另建或继续使用旧 Phase 1 `plan_version` 作为结果身份。所有日事实、覆盖、汇总和发布指针只通过 `batch_id` 关联。

`mrp_calc_batch` 必须绑定：

```text
forecast_version_id
inventory_snapshot_id
bom_snapshot_id（仅历史批次）
bom_source_checksum（新批次当前同步 BOM 的内容校验码）
category_role_version_id
material_policy_version_id
production_calendar_version_id
factory_warehouse_mapping_version_id
inventory_control_policy_version_id
planning_week_version_id
decision_checksum
rule_version
input_checksum
result_checksum
version_no
active_run_id
revision_of_batch_id
revision_no
superseded_by_batch_id
published_at
```

`shipment_batch_id` 本期仅允许作为 nullable 预留字段，不进入公式、checksum 或发布门禁，标记 `TODO-SHIPMENT`。

输入或配置变更时，相关未发布批次必须进入 `STALE`。同一计算不得查询“当前最新”版本补齐数据。

## 3. 最小实体集合

| 实体 | 作用 | 不变量 |
|---|---|---|
| `forecast_version/detail` | 整机月经营计划 | 确认后不可变；持久化 source_row_no/priority_seq |
| `inventory_snapshot/scope` + `mrp_inventory_balance` | 快照组、仓库范围和 Phase 2 规范化库存真源 | 统一导入可含多个已维护仓库；余额按仓库+物料+品质汇总；数量 DECIMAL；缺行与零值不同 |
| `mrp_factory` + `factory_warehouse_mapping` | 工厂与仓库当前主数据 | 工厂编码、名称必填；一厂多仓，仓库编码全局唯一；仓库良品属性和正整数优先级按批次冻结 |
| `factory_warehouse_mapping_version/detail` | 工厂仓库映射快照 | 新批次一厂多仓，按优先级升序扣减，未指定时按首次维护顺序；历史版本只读 |
| `inventory_control_policy_version/detail` | 安全/预警库存 | 仓库+物料唯一；0<=safety<=warning |
| `planning_week_version/definition` | 业务周 W1-Wn | 日期不重叠、展示区间无空档，发布后不可变 |
| `mrp_category_role_version/detail` | 分类角色 | 发布后不可变 |
| `mrp_material_policy_version/detail` | 供应方式和下钻策略 | 两字段独立，发布后不可变 |
| `bom_detail` / 历史 `bom_snapshot/detail` | 当前同步 BOM / 历史批次输入 | 新批次读取 BOM 管理当前同步关系并记录 checksum；历史批次只读原快照 |
| `production_calendar_version/day` | 工厂生产日历 | 工厂+版本+日期唯一 |
| `mrp_calc_batch` | 预检、决策、计算和发布批次 | 绑定全部输入和规则 |
| `mrp_decision_issue/record` | 业务不确定项及决定 | issue_key 幂等；有审计/revision |
| `mrp_inventory_pool/allocation` | 共享库存池、自动优先级分配和人工覆盖 | 按来源月+经营计划顺序；不超扣除安全库存后的可分配量和毛需求 |
| `mrp_requirement_detail` | 逐层毛/库存/净需求 | 路径和终止原因可解释 |
| `mrp_terminal_demand` | 来源整机到末级物料月需求 | 保留 root+terminal+sourceMonth |
| `mrp_calc_run` | 一次隔离的计算结果写入运行 | run_id、batch_id、状态、行数和 checksum；只有 active run 可查询 |
| `mrp_daily_plan_detail` | 不可变日自动排产事实 | batch_id+稳定行键+日期唯一，只存系统量 |
| `mrp_daily_plan_override` | 当前日人工覆盖及历史 | batch_id+人工量与系统量分离，原因和操作者必填 |
| `mrp_daily_execution` | 已执行/锁定和实际完成事实 | rowKey+日期唯一；状态、实际量、确认人、时间、原因、revision 与审计完整 |
| `mrp_unallocated_quantity` | 非生产日等未定落点 | 归零前不得发布 |
| `mrp_calc_exception` | 技术/业务异常 | 严重级别和范围可追溯 |
| `calc_task/import_task/audit_log` | 异步任务和审计 | MySQL 是状态真源 |

## 4. 状态机

批次状态：

```text
DRAFT -> CHECKING -> ANALYZING -> WAITING_DECISION
      -> REANALYZING -> READY_TO_CALCULATE -> CALCULATING
      -> WRITING -> READY -> PUBLISHED
```

`ANALYZING` 是从根节点向下展开、发现缺失决策/共享库存需求的分析波次；无阻断时可直接进入 `READY_TO_CALCULATE`。发现阻断时进入 `WAITING_DECISION`；业务处理后只能进入 `REANALYZING`，可再次回到 `WAITING_DECISION` 或在稳定后进入 `READY_TO_CALCULATE`。只有所有波次稳定、所有决策已锁定并生成 `decision_checksum` 后才允许计算。`WRITING` 期间结果对查询不可见。未发布批次可进入 `STALE`；技术失败进入 `FAILED`；安全取消进入 `CANCELLED`。`READY/PUBLISHED` 的计算结果不可回写。

合法迁移条件冻结如下：

```text
DRAFT -> CHECKING
CHECKING -> ANALYZING | FAILED
ANALYZING -> READY_TO_CALCULATE | WAITING_DECISION | FAILED
WAITING_DECISION -> REANALYZING | CANCELLED
REANALYZING -> READY_TO_CALCULATE | WAITING_DECISION | FAILED
READY_TO_CALCULATE -> CALCULATING | STALE | CANCELLED
CALCULATING -> WRITING | FAILED | CANCELLED
WRITING -> READY | FAILED
READY -> PUBLISHED | STALE
```

`mrp_calc_run` 状态为 `CREATED -> WRITING -> VALIDATING -> ACTIVE`，或从 `WRITING/VALIDATING` 到 `FAILED/CANCELLED`。只有持有批次版本锁的 `MrpResultWriter` 能在校验通过的短事务内把一个 run 置 `ACTIVE` 并更新 `active_run_id`；同一批次只能有一个 ACTIVE run，旧 run 保留为历史但不作为当前查询结果。月中重算必须新建 run，禁止覆盖旧 run。

配置版本状态使用 `DRAFT -> PUBLISHED -> RETIRED`。新配置通过新版本发布，不修改历史版本。

已发布批次不可修改计划事实或覆盖记录。发布后调整必须创建新的修订批次，`revision_of_batch_id` 指向最初或直接前序发布批次，`revision_no` 单调递增。新修订发布时，以同一短事务设置前序批次 `superseded_by_batch_id`；旧批次仍保持历史可查。执行状态属于独立运营事实，可在已发布批次上受控确认，但不得改变已发布结果 checksum。

## 5. 日事实键与聚合

日计划行键至少包含：

```text
batch_id
factory_code
root_material_code
terminal_material_code
path_group_key
schedule_date
```

若主表按多路径汇总，`path_group_key` 必须采用已冻结的汇总规则，路径明细仍保存在 `mrp_requirement_detail`。不同来源整机绝不能因末级物料相同而合并。

API 的 `manualQuantity` 来自当前有效 override，`effectiveQuantity = COALESCE(manualQuantity, systemQuantity)`；`effectiveQuantity` 是查询投影，不是第二个可独立修改的真源。周/月汇总不是可编辑事实：

```text
effective = COALESCE(mrp_daily_plan_override.manual_quantity,
                     mrp_daily_plan_detail.system_quantity)
weekly = SUM(effective by published business week range)
business_monthly = SUM(effective by all W1-Wn of business month)
natural_monthly = optional read-only SUM(effective by schedule month)
```

需要性能优化时只能建立可重建的只读汇总视图/缓存，并以日事实为真源。

`mrp_daily_execution` 至少包含：

```text
batch_id, row_key, schedule_date,
execution_status PLANNED|LOCKED|EXECUTED,
actual_quantity, confirmed_by, confirmed_at, remark,
revision, created_at, updated_at
```

`LOCKED/EXECUTED` 日期不允许日重分配、重算覆盖或修订覆盖。确认时若 `actual_quantity != effective_quantity`，生成 `EXECUTION_QUANTITY_MISMATCH` 异常；缺少实际量不得确认 `EXECUTED`。

## 6. 事务

- 配置发布：版本头、明细、checksum、状态和审计同一短事务；
- 库存确认：原始导入到规范化快照、明细、checksum 和审计同一事务；
- 预检：批次与幂等 issue 同一短事务；
- 决策/库存分配：按 expected revision 乐观锁提交，审计同事务；
- 执行确认：执行记录、差异异常和审计同一短事务；
- 锁定计算：确认 blocker=0、待分配=0、输入 checksum 未变化并冻结 decision checksum；
- 计算阶段不持有长事务；
- 计算运行：先创建 `mrp_calc_run`，结果写入 staging/运行分区；每批 500-2000 行使用独立短事务，状态保持 `WRITING` 且不可查询；
- 最终提交：校验行数、守恒、checksum 和 run 状态后，用真正短事务将 `mrp_calc_batch.active_run_id` 切换到该 run 并置 `READY`；旧 active run 保留为历史，失败 run 隔离并异步清理；
- 发布：全部门禁、状态变更和审计同一事务；
- 修订发布：新批次发布、前序版本失效指针和审计同一短事务；
- 任何失败不得留下可查询为 `READY` 的半版本。

## 7. 索引与批量

至少覆盖：

```text
mrp_calc_batch(status, created_at)
mrp_calc_batch(factory_code, input_checksum, status)
mrp_decision_issue(batch_id, status, severity, issue_type)
mrp_inventory_balance(inventory_snapshot_id, factory_code, inventory_org, material_code, inventory_quality)
mrp_inventory_pool(batch_id, factory_code, material_code)
mrp_inventory_allocation(pool_id, demand_key)
mrp_requirement_detail(batch_id, root_material_code, current_material_code)
mrp_daily_plan_detail(batch_id, factory_code, schedule_date)
mrp_daily_plan_detail(batch_id, root_material_code, terminal_material_code, schedule_date)
mrp_daily_execution(batch_id, row_key, schedule_date)
mrp_requirement_detail(run_id, root_material_code, source_month, path_hash, current_material_code)
mrp_terminal_demand(run_id, root_material_code, terminal_material_code, source_month)
mrp_unallocated_quantity(run_id, demand_key, source_date, attempted_schedule_date)
mrp_calc_exception(run_id, severity, error_code, scope_key)
production_calendar_day(version_id, factory_code, calendar_date)
bom_snapshot_detail(snapshot_id, parent_code)
```

MyBatis 批量写入每批 500-2000 行；禁止逐行查物料、BOM、库存或日历。日网格和工作台必须服务端分页，默认 20、最大 100。

## 8. 迁移兼容

- 已应用 Flyway 迁移不可修改、删除或重编号；执行时重新扫描最高版本并从下一号新增；
- 当前工作树观察到 V019/V020，不能假定它们已提交或可覆盖；
- 不原地把 `plan_detail` 改为日表，不复用旧 `plan_override` 键；
- 旧 `BIGINT` 数据通过兼容查询读取，新引擎不把小数写回旧表；
- 新批次预检和计算读取 `bom_detail` 当前同步数据并核对 `bom_source_checksum`；历史批次仍读取原 `bom_snapshot`；NC 是否默认不作为排产下钻筛选条件；
- `inventory_item` 是原始/业务结构，计算只读规范化库存真源，两表不得相加。
- 通过新迁移为库存原始行和规范化余额增加 `inventory_quality`，只允许 `GOOD/DEFECTIVE`；不得修改 V001-V020。

## 2026-10-14 日产能数据补充

V044 为当前/快照日历新增 capacity_unit，当前记录新增 revision；mrp_calendar_material_unit 以(calendar_version_id,material_code)保存物料单位。历史单位不回填，显示未评估。日历快照与物料单位随新批次冻结，修订创建新快照。人工转移使用现有 mrp_daily_plan_override 保存两端，不改系统事实；空目标作为system0投影，发布转为独立有效日事实。

## 2026-10-14 整机产出数据（C019）

沿用现有需求/日事实表，terminal_type增加语义MACHINE_OUTPUT（VARCHAR(32)可容纳，无需改已应用迁移）。根整机为terminal_material_code，独立于根STOP末级需求，数量来自成功计算的根level0 netQty；整数台、按需求/月守恒。库存仍只在原净需求链路扣一次。所有事实参与run/人工覆盖/执行锁/发布复制/checksum，查询按view隔离。新规则v3-whole-machine-capacity；历史v2不回填整机事实或改写单位。
