# MRP 多层净需求与日排产计算契约

本文件是 Phase 2 算法最高优先级契约。Phase 1 的 W1-W3/W4 carry 算法只用于历史版本读取，不得用于新计算。

## 1. 锁定输入

一次计算只读取已绑定且已发布/确认的经营计划、规范化库存、分类角色、物料策略、生产日历和业务决策版本。新批次 BOM 使用管理页面当前最新同步数据，批次保存校验码；预检、计算和发布前验证未变化。历史批次保留原 BOM 快照。已出货本期完全忽略，标记 `TODO-SHIPMENT`。

经营计划、库存和配置沿用原有版本规则；BOM 当前同步内容变化时，新批次必须重建。计算过程中不得混用两次同步的 BOM 或等待人工输入。

## 2. 多层净需求

对来源整机、工厂和来源月份：

```text
rootGross = forecast
unitUsage = childQty / parentQty
childGross = parentNet * unitUsage
nodeNet = MAX(0, nodeGross - confirmedInventoryAllocation)
```

规则：

1. 整机层先扣整机库存；
2. 每个中间节点先扣本节点库存；
3. 只把净需求传给下级；
4. `STOP` 使当前物料成为末级需求；
5. `EXPLODE` 且有有效子项时继续；
6. 无有效子项的结构叶子成为末级需求；
7. 下钻策略缺失或历史无效值默认 `EXPLODE`，继续至结构叶子；有效 `STOP` 即使缺供应方式仍生效；
8. 物料策略缺失不阻断；按工厂+物料去重保存缺失字段、实际下钻动作及全部来源整机/月/BOM 路径，随计算 run 冻结并在排产完成后展示。供应方式缺失保持未配置，不推断为自制/外购。未维护角色、BOM 循环、父项数量小于等于 0、子项数量小于等于 0、缺物料或多份有效 BOM 未裁决均阻断；
9. 根节点 `EXCLUDED` 必须由业务明确选择跳过或修正后才能继续；中间节点 `EXCLUDED` 一律阻断，禁止静默丢弃数量；
10. 同物料多个来源竞争不足库存时，系统先按来源月、再按经营计划已冻结的 `priority_seq` 自动分配；不得使用 Java/SQL 遍历顺序或前端排序作为隐式优先级。
11. BOM 来源在 NC 同步时按“库存组织+父料号+子料号”选择最高有效版本；新批次读取当前同步表，不根据是否默认字段筛选；重复最高版本、有效期重叠或组织不明确时阻断。
12. MVP 不做计量单位换算；所有输入数字直接参与计算，禁止根据单位名称隐式乘除或阻断。

黄金示例：

```text
A forecast=1000, A inventory=100
A -> B x1, B inventory=200
A -> C x2, C inventory=300
B -> D x1, D inventory=200
C/D terminal

A net=900
B gross=900, net=700
D gross=700, net=500
C gross=1800, net=1500
terminal: C=1500, D=500
```

每个节点必须保存 gross、inventoryBefore、inventoryUsed、inventoryAfter、net、路径和终止原因。

## 3. 库存分配

- 当前库存导入按明细行的仓库编码匹配已维护仓库，一份文件可包含多个仓库；工厂通过仓库归属确定，批次冻结本厂仓库范围，历史快照仍按原范围头回放；
- 原始库存按快照+仓库+物料+`inventory_quality` 汇总；仅 `GOOD` 进入物理良品库存，`DEFECTIVE` 不参与扣减但必须保留追溯；
- 有 0 数量记录表示库存为 0；无记录生成 `INVENTORY_MISSING`；
- 缺记录只有业务确认“本批次按 0”、修正快照或切换快照后才能继续；
- 分配约束：`0 <= allocated <= gross`；
- 安全库存作为硬保底：`reserved = MIN(physical_good_qty, safety_stock_qty)`，`allocatable = MAX(0, physical_good_qty - safety_stock_qty)`；
- 池约束：`sum(allocated) + reserved <= physical_good_qty`；
- 上游输入 checksum 变化使分配失效；
- 分配锁定后才允许计算；
- 库存余额不得为负，不得在不同路径重复使用。
- 同一快照库存池覆盖本批次全部来源月份，不能每月重新恢复初始库存。
- 自动分配使用稳定键 `source_month ASC, priority_seq ASC, root_material_code ASC, current_material_code ASC, demand_key ASC`，依次分配 `MIN(remaining_allocatable, gross)`。
- 系统分配记录标记为 `AUTO_PRIORITY`；业务可在分配工作台显式覆盖为 `MANUAL_OVERRIDE`，必须填原因、校验 revision 并重新生成 checksum。
- 分配后投影余量低于预警线时生成风险提示，默认不阻断净需求和末级需求的生成。

## 4. 日需求分配

末级月净需求只分配到来源月份内的有效生产日：

```text
rawDayQty = monthlyTerminalNet * dayWeight / sum(monthWeights)
```

- 新规则 `v2-daily-capacity` / `v3-whole-machine-capacity` 固定所有有效生产日权重为 1，等权初排；仅历史批次继续读取原冻结权重；
- 非生产日不参与来源日分配；
- 整件物料使用最大余数法：先向下取整，再按余数降序、日期升序补足；
- 小数物料按物料精度量化，残差按稳定顺序回补；
- 月内无有效生产日生成阻断问题；
- 来源月所有日数量之和必须等于该月末级净需求。

## 5. 提前 7 天

```text
scheduleDate = sourceDate - 7 calendar days
```

- 使用自然日，跨月、跨年、闰年均按真实日期；
- 目标日期为生产日时写入日计划；
- 目标日期为非生产日时写入待分配池，不自动寻找相邻生产日；
- 业务分配到合法生产日后才写最终日计划；
- 展示未来 12 周时，计算输入覆盖到展示截止日后至少 7 天。

页面和主查询只使用最终 `scheduleDate`，不展示“实际排产周/来源需求周”。`sourceMonth/sourceDate` 只用于详情追溯和守恒。

## 6. 日事实与汇总

`systemQuantity` 保存自动值，`manualQuantity` 可为空，`effectiveQuantity` 优先人工值。人工值不得覆盖系统值。

```text
businessWeeklySummary = SUM(effectiveQuantity where scheduleDate between publishedWeek.startDate and publishedWeek.endDate)
businessMonthlySummary = SUM(businessWeeklySummary for published businessMonth W1-Wn)
calendarMonthlySummary = SUM(effectiveQuantity by calendar schedule month)  // 可选只读口径
```

业务周由已发布 `planning_week_definition` 定义，可跨自然月且不固定四周。周/月只读，不生成第二套可编辑结果。日调整后汇总必须立即一致。

## 7. 人工调整

- 同一行的多日重分配在一个事务内提交；
- 同一已发布业务周范围内调整必须守恒；
- 跨业务周范围调整必须填写原因并审计；
- 暂存差额时计划状态进入待调整，禁止发布；
- 恢复自动值只删除指定日覆盖；
- 并发修改使用 expected revision，冲突返回 409。
- MVP 只允许多日重分配并保持同一行总量不变；不得用单日 PUT 绕过守恒。跨周调整必须填写原因并审计。

## 8. 发布校验

```text
all blocking issues resolved
unallocated quantity = 0
all schedule dates are production days
gross = inventoryUsed + net at every node
childGross = parentNet * childQty / parentQty
sum(source-month daily demand) = monthly terminal demand
sum(daily effective) = weekly/monthly summaries
manual reallocations balanced
input checksum unchanged
locked/executed dates unchanged
```

任一失败不得进入 `READY/PUBLISHED`。Phase 2 MVP 不把旧周产能作为发布阻断或 checksum 输入；日产能按 2026-10-14 C019 评估并确认异常；产线自动分配和自动平移仍为 TODO。

## 9. 已执行日、月中重算与修订

- 当前执行状态和实际完成量由业务手动确认；`EXECUTED/LOCKED` 日期不可被重算或人工重分配覆盖；
- `EXECUTED` 必须有实际完成量、确认人和确认时间；实际量与当时生效计划量不一致时生成 `EXECUTION_QUANTITY_MISMATCH`；
- 月中重算只计算未来未锁定日期，每次创建新的 `mrp_calc_run`，旧 run 保留；
- 月中重算后的库存分配是保留还是重新分配尚未冻结，受影响批次在决策前只能生成差异预览，禁止发布；
- 发布后的修改必须创建新修订批次，已发布批次不可原地修改；修订批次只能改变未来未锁定日期，并完整重走预检、计算和发布门禁。

## 10. 可重复性

- 全链路使用 BigDecimal，不使用 float/double 或无约束 long 转换；中间计算使用 `MathContext.DECIMAL128`、至少 12 位中间小数，按物料 `quantity_scale`/持久化 6 位显式 HALF_UP 量化；非终止除法、深层乘法或聚合溢出必须阻断并记录异常；
- BOM 子项、需求来源、日期和结果均稳定排序；
- 输入和结果使用明确字符集与 SHA-256 等稳定散列；
- checksum 包含所有绑定版本、规则版本和 decision checksum；
- 已出货字段和数据变化不得改变本期 input/result checksum；
- 相同输入和决策重复运行必须产生同样的逐层明细、日计划和 checksum。

## 2026-10-14 日产能补充契约（C018）

新规则 v2-daily-capacity 等权初排；历史权重保留。最终日期仍=来源日期−7自然日。整批工厂/日期有效排产中仅同单位 SELF_MADE 计入能力，外购/委外排除；缺配置/供应/单位和不匹配显式未评估。负荷>能力超限，等于正常，停产有自制负荷异常；部分未知且已知量超限同时提示。查询筛选分页不缩小统计母集。

跨日转移同一来源需求，原子保存两端人工量，保留系统值，空日期生成system=0投影；sourceMonth不变，日周月守恒。校验activeRun、两端有效量、revision及执行锁；新规则禁止单端覆盖/恢复。锁定/执行冻结当前effectiveQuantity，解锁后重新冻结也更新plannedQuantity。发布复制有效投影为独立事实，确认指纹包含完整需求身份和有效量；即使当天总负荷不变，需求行变化也使旧确认失效。修订冻结最新日历/物料单位，保留已锁/已执行量。

## 2026-10-14 整机日产能更正（C019，优先于C018）

v3-whole-machine-capacity：根level0 netQty作为整机需生产数量，独立生成MACHINE_OUTPUT事实，单位台，整数最大余数等量分配。BOM子项倍数、库存、供应方式及终止层级不参与整机负荷推算。scheduleDate仍=sourceDate−7，不重设产出日期。

当天能力只比较本批次整机有效日计划合计；物料视图/筛选/分页不改变统计母集。旧非台能力未评估、不自动回填。整机与末级日事实分别按完整身份守恒，周/月按view隔离；整机跨日调整只接受整数，不自动移动末级备料。发布、执行锁、修订和确认指纹覆盖两类事实。历史v2保持C018口径。


## 2026-10-15 已批准v4滚动规则

历史v4批次采用 `v4-rolling-business-weeks`（新批次以文末C022的v5为准）。按来源月和同月经营计划导入顺序逐层扣良品、安全库存硬保底；双厂各自仓库隔离。净需求三份（余数归第一份）分别为上月末业务周、本月W1、本月W2，周内按生产日均分，无额外减7天。周按冻结业务周、月按实际生产自然月汇总。联动转移整机与末级数量均为整数；建议HALF_UP且上限floor(末级剩余量)，原始BOM小数保持守恒。完整规则见source-precedence C020及已批准设计文档；下文旧规则仅适用于相应历史rule_version。

## 2026-10-19 C022：按实际业务周数均分并整体提前一周（用户已确认）

新建计划采用 `v5-shifted-business-weeks`，替代新计划原有的固定三份规则。来源月有 N 个冻结业务周，则按 N 份均分月净需求；基本份额向下取整，全部周余数放第一份。第一份排到上月最后一个业务周，其余份额依次排到本月第1至第 N−1 个业务周。本月最后业务周承接下月第一份；末月没有后续需求时该周留空。不额外减7个自然日。

例：10月4个业务周、净需求1003，分为253/250/250/250，分别排到9月末周及10月前3周。11月若有5个业务周、净需求1003，则分为203/200/200/200/200，分别排到10月末周及11月前4周。周内按本厂生产日均分，日余数从早到晚分配。整机使用整数；末级小数保留6位精度，数量守恒。

日事实保留来源月，周按冻结业务周、月按实际生产自然月汇总。缺少业务周、业务周配置异常、生产日历不完整或整周停产时，对应数量进入待分配池。既有v4及更早批次（含其修订版）继续使用原规则；需使用新规则时新建计划。
