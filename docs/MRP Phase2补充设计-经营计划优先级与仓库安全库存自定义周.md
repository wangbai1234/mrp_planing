# MRP Phase 2 补充设计：经营计划优先级、仓库安全库存与自定义周

> 设计日期：2026-09-29  
> 状态：业务规则已确认，待按本文实施  
> 适用范围：MRP Phase 2 新排产中心，不改写 Phase 1 历史周排产

## 1. 本次冻结的业务决策

1. 多个整机共享同一物料库存时，同一来源月内按经营计划顺序分配；更早来源月优先于更晚月份。
2. 库存覆盖永惠、爱培科、惠州仓。一个工厂在同一发布版本中只对应一个仓库，计算只能扣减该工厂映射仓库的库存。
3. 良品库存增加安全库存和预警库存。安全库存是不得自动消耗的保底量；预警库存是风险提示线。
4. 日排产仍是唯一事实。业务可按月人工维护 W1、W2、W3 等日期范围，周汇总按已发布的业务周范围聚合，不再写死为周一至周日。

## 2. 真实代码审计结论

| 链路 | 当前实现 | 缺口 |
|---|---|---|
| 经营计划 | `forecast_detail` 没有优先级/原始行号；Mapper 按料号排序；根需求在 `groupingBy` 后遍历 | 导入顺序不能稳定进入计算 |
| 库存 | 规范化只按物料+品质汇总；计算再合并快照下所有 `GOOD` | 永惠、爱培科、惠州仓会被混合 |
| 安全库存 | `mrp_inventory_pool.reserved_qty` 已有字段，但建池时始终写 0 | 无配置、无预警线、无风险投影 |
| 周汇总 | 日网格和周汇总使用 `previousOrSame(MONDAY)` | 无法支持业务定义的 W1-Wn |
| 排产版本 | `mrp_calc_batch` 只有一个 `factory_code` | 单工厂批次与“唯一当前排产版本覆盖多工厂”冲突 |

## 3. 总体模型

### 3.1 一个当前排产版本，多个工厂范围

`mrp_calc_batch` 继续作为业务看到的唯一排产版本，新增 `mrp_batch_factory_scope` 表示版本内的工厂范围：

```text
mrp_calc_batch
  1 -> N mrp_batch_factory_scope
          factory_code
          warehouse_code
          inventory_snapshot_scope_id
          production_calendar_version_id
          status / checksum
```

业务只创建、发布一个当前排产版本。Worker 按工厂范围分区计算，所有范围通过后才整体发布。当前批次表的 `factory_code` 仅保留过渡兼容，新计算不得以它作为唯一范围。

### 3.2 新增三类版本化配置

1. **工厂仓库映射版本**：决定每个工厂使用哪个仓库。
2. **库存控制策略版本**：按仓库+物料维护安全库存和预警库存。
3. **业务周期间版本**：维护业务月下 W1-Wn 的开始日和结束日。

三类配置均使用 `DRAFT -> PUBLISHED -> RETIRED`，已发布版本不可原地修改，并进入批次 `input_checksum`。

## 4. 经营计划顺序与共享库存

### 4.1 优先级真源

- `forecast_detail` 新增 `source_row_no` 和 `priority_seq`。
- 默认以 Excel 数据行顺序生成 `priority_seq`，从 1 连续递增。
- 导入预览页显示“优先级”；确认导入前可调整，确认后随经营计划版本冻结。
- 同一版本的 `factory_code + material_id` 只允许一行根记录；重复行阻断导入，避免优先级不明确。

### 4.2 确定性分配顺序

每个工厂/仓库库存池内，需求组按以下键排序：

```text
source_month ASC
priority_seq ASC
root_material_code ASC
current_material_code ASC
demand_key ASC
```

含义为“先保较早月份，同一月内按经营计划顺序”，避免未来月需求先消耗当前月库存。

### 4.3 两阶段分配

1. 分析波次先按 `仓库+物料+品质` 建库存池，按 `来源整机+来源月` 汇总同一物料的多 BOM 路径需求。
2. 按固定顺序自动分配可用库存，生成 `AUTO_PRIORITY` 分配记录。
3. 同一整机+月份内有多条路径时，分配量按路径毛需求比例回分，尾差按稳定路径键回补。
4. 库存分配工作台展示“系统顺序、系统分配、人工调整、最终分配”。人工改动使用 `MANUAL_OVERRIDE`，必须填原因并校验 revision。

经营计划顺序、BOM、库存快照、工厂仓库映射或安全库存策略变化后，旧分配失效，不得静默沿用。

## 5. 工厂、仓库与库存快照

### 5.1 工厂仓库映射

`factory_warehouse_mapping_detail` 包含：

```text
version_id
factory_code
warehouse_code
warehouse_name
enabled
revision
```

约束：

- `version_id + factory_code` 唯一；
- `version_id + warehouse_code` 唯一；
- 工厂来自已启用产能配置；
- 仓库初始范围是永惠、爱培科、惠州仓，具体编码和映射由业务发布，代码不硬编码关系。

### 5.2 库存快照组

```text
inventory_snapshot
  1 -> N inventory_snapshot_scope
          warehouse_code
          snapshot_date
          source_file
          status
          1 -> N inventory_item (material + quality + quantity)
```

导入弹窗先选仓库，再上传该仓库文件。每份文件只属于一个仓库，不在明细行重复维护工厂和库存组织。规范化键为：

```text
snapshot_id + warehouse_code + material_code + inventory_quality
```

创建 MRP 批次时，系统按已发布映射自动锁定仓库范围，用户不手工拼接。参与排产的工厂没有对应仓库快照时，生成 `WAREHOUSE_SNAPSHOT_MISSING` 阻断项。

## 6. 安全库存、预警库存与保底

### 6.1 配置与公式

`inventory_control_policy_detail` 按 `warehouse_code + material_code` 维护：

```text
safety_stock_qty       安全库存，硬保底
warning_stock_qty      预警线
effective_from
remark
```

必须满足 `0 <= safety_stock_qty <= warning_stock_qty`。页面支持单条维护和 Excel 导入。

```text
physical_good_qty = 该仓库良品库存
hard_reserve_qty = MIN(physical_good_qty, safety_stock_qty)
allocatable_qty = MAX(0, physical_good_qty - safety_stock_qty)
projected_balance_qty = physical_good_qty - allocated_qty
sum(allocated_qty) + hard_reserve_qty <= physical_good_qty
```

`mrp_inventory_pool.reserved_qty` 保存 `hard_reserve_qty`。系统不自动释放安全库存；紧急释放需要后续独立审批需求，MVP 不提供绕过保底的快捷操作。

### 6.2 预警状态

| 状态 | 条件 | 处理 |
|---|---|---|
| 正常 | `projected_balance >= warning_stock` | 正常展示 |
| 预警 | `safety_stock <= projected_balance < warning_stock` | 黄色预警，不阻断计算 |
| 低于安全库存 | `projected_balance < safety_stock` | 红色风险，引擎仍不消耗保底量 |
| 需求缺口 | 可分配库存用完后仍有净需求 | 显示缺口，继续生成末级需求 |

预警默认不阻断 MRP 计算或发布；否则库存越紧张越无法生成缺口计划。配置缺失生成 `INVENTORY_POLICY_MISSING`，过渡期可由业务批量确认按 0，但必须留痕。

## 7. 业务周期间

### 7.1 数据结构

```text
planning_week_definition
  version_id
  business_month
  week_code            W1 / W2 / W3 / W4 / W5 ...
  sequence_no
  start_date
  end_date
  remark
```

发布校验：

- `start_date <= end_date`；
- 同一版本任意日期只属于一个业务周；
- 同一业务月的 `sequence_no` 连续，`week_code` 不重复；
- 排产展示区间的每个 `schedule_date` 都有周定义；
- 允许 W1 跨自然月，例如 10 月 W1 可为 9/28-10/4；
- 每月可按实际维护 W1-W4、W1-W5 或更多范围，不固定四周。

### 7.2 与日排产的关系

- 月需求分日、提前 7 天和非生产日处理仍以真实日期计算；
- 业务周只决定日网格表头、周汇总和周产能对照，不产生第二套可编辑周事实；
- 周汇总为 `SUM(effective_quantity WHERE schedule_date BETWEEN start_date AND end_date)`；
- 月汇总默认按 `business_month` 汇总 W1-Wn；自然月可作为另一个只读口径，标题必须明确区分。

## 8. 页面设计

### 8.1 配置菜单

```text
配置
├─ 产能配置
├─ 工厂仓库映射
├─ 库存控制策略
├─ 物料策略
├─ 生产日历
└─ 业务周期间
```

### 8.2 库存快照页

- 快照头显示快照日期、要求仓库数、已导入仓库数和状态；
- 仓库卡片分别显示永惠、爱培科、惠州仓导入情况；
- 详情表显示“仓库、品质、良品数量、安全库存、可分配数量、预警状态”；
- 缺少必需仓库文件时不能确认完整快照，除非本批次明确不包含该工厂。

### 8.3 库存分配工作台

表格显示：

```text
仓库 | 共享物料 | 来源月 | 经营计划优先级 | 来源整机
毛需求 | 系统分配 | 人工调整 | 最终分配 | 分配后余量 | 预警状态
```

默认按计算优先级排序，不允许前端自由排序改变计算含义。用户可筛选、查看和显式调整，调整后必须重新分析并生成新 checksum。

### 8.4 业务周页和排产中心

- 新增“配置 -> 业务周期间”，按月维护 W1-Wn，使用 Element Plus 日期范围选择器；
- 支持复制上月规则后再调整，发布前检查空档、重叠和展示范围覆盖；
- 日排产表头显示“2026-10 W1（9/28-10/4）”；
- 新建排产时自动锁定已发布工厂仓库映射、库存控制策略和业务周期间，不让用户手工拼映射。

## 9. 事务、并发和可重算性

1. 经营计划确认导入在单个短事务中保存明细和优先级，不允许部分行已确认。
2. 快照组只有在本次所需仓库都成功规范化后才可标记 `IMPORTED`。
3. 库存池创建和自动分配在独立 Writer 短事务中执行；先写 candidate run，守恒校验成功后再切换 active run。
4. 分配更新同时校验 allocation revision 和 pool version；冲突返回 409，不静默覆盖。
5. 映射、安全库存、业务周、经营计划优先级均进入快照和 checksum。
6. 计算不依赖 Java `HashMap` 顺序、无 `ORDER BY` SQL 结果或前端表格排序。

## 10. 数据库与 API 建议

只能新增 Flyway 迁移，不修改 V021-V029。建议新增：

```text
factory_warehouse_mapping_version/detail
inventory_control_policy_version/detail
planning_week_version/definition
inventory_snapshot_scope
mrp_batch_factory_scope

forecast_detail.source_row_no / priority_seq
mrp_calc_batch.factory_warehouse_mapping_version_id
mrp_calc_batch.inventory_control_policy_version_id
mrp_calc_batch.planning_week_version_id
mrp_inventory_balance.warehouse_code / safety_stock_qty / warning_stock_qty / allocatable_quantity
mrp_inventory_pool.warehouse_code
mrp_inventory_allocation.priority_seq / allocation_mode / override_reason
```

```text
GET/POST /api/v1/mrp/factory-warehouse-mapping-versions
GET/POST /api/v1/mrp/inventory-control-policy-versions
GET/POST /api/v1/mrp/planning-week-versions
POST     /api/v1/inventory/snapshots/{id}/scopes/{warehouseCode}/upload
GET      /api/v1/inventory/snapshots/{id}/warehouse-summary
GET/PUT  /api/v1/mrp/batches/{id}/inventory-allocations
GET      /api/v1/mrp/batches/{id}/inventory-alerts
GET      /api/v1/mrp/plans/{id}/weekly-summary
```

## 11. 黄金验收案例

### 11.1 优先级+安全库存

```text
永惠仓共享物料 X 良品库存 150
安全库存 20，预警库存 50，可分配 130

2026-10：
优先级 1 整机 A，X 毛需求 100
优先级 2 整机 B，X 毛需求 100

结果：A 分配 100，B 分配 30，保留 20，B 净需求 70。
分配后库存 20 低于预警线，显示红色风险，但继续生成 MRP 需求。
```

### 11.2 仓库隔离

```text
永惠仓 X=100，爱培科仓 X=50，惠州仓 X=200。
永惠工厂映射永惠仓后只能使用 100，不得读取另外 250。
某仓缺少物料行时，只对该仓/工厂生成 INVENTORY_MISSING。
```

### 11.3 自定义周

```text
2026-10 W1 = 2026-09-28 .. 2026-10-04
2026-10 W2 = 2026-10-05 .. 2026-10-11
2026-10 W3 = 2026-10-12 .. 2026-10-18
2026-10 W4 = 2026-10-19 .. 2026-10-25
2026-10 W5 = 2026-10-26 .. 2026-11-01
```

2026-09-30 的日排产必须进入 `2026-10 W1`，不进入系统推算的自然周。W1-W5 合计必须等于该业务月全部日有效数量。

## 12. 实施顺序

1. 新增迁移、领域对象和三类版本发布服务。
2. 修复经营计划导入，冻结原始行号和优先级。
3. 改造库存快照组、工厂仓库映射和库存规范化。
4. 将安全库存写入 pool reserved，实现预警投影。
5. 重构净需求为“需求分析 -> 稳定排序分配 -> 路径回分 -> 逐层传播”。
6. 改造日网格和周汇总，读取已发布业务周。
7. 完成配置页、库存页、分配工作台和排产中心锁定输入。
8. 按第 11 节黄金案例、三层验证和 Docker 空库/升级流程闭环。

