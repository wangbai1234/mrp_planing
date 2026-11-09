# MRP Phase 2 修复后逐项测试执行手册

## 1. 使用规则

本手册用于 Mimo 验收本轮修复。每个用例必须实际执行并记录 `PASS`、`FAIL` 或 `BLOCKED`，禁止用百分比、日志“完成”或空断言代替结果。某用例失败时先修复，再重跑该用例和所属批次；所属批次全绿后才进入下一批次。

禁止：`if (visible) skip`、`count >= 0`、固定历史批次、手工改生产库制造结果、只验证 HTTP 200、只截图不核对数据、排除 Testcontainers 测试。

所有测试批次使用唯一 `testRunKey`、工厂编码和料号；数量按字符串/Decimal 比较，不使用浮点数。正式数据必须通过页面或正式 API/导入流程准备。

## 2. 环境与证据

```bash
cd backend && ./mvnw compile
cd backend && ./mvnw test
cd frontend && npm run build
cd frontend && npm test -- --run
cd frontend && npx playwright test --project=chromium
curl -fsS http://localhost:8080/actuator/health
```

每批证据保存到 `test-evidence/phase2/<testRunKey>/`：请求 JSON、响应 JSON、关键 SQL 输出、浏览器截图、Playwright trace、服务日志。SQL 只读查询必须带 `testRunKey` 或本批次 ID。

Docker 兼容错误只能标 `BLOCKED`，不能改依赖、跳过或伪造 `PASS`。

## 3. G0 静态门禁

1. 检查 `prototype/MRP日排产Phase2可交互原型.html` 存在；不存在则本批 `FAIL`。
2. 检查所有 Phase 2 数量 Java 字段为 `BigDecimal`、数据库为 `DECIMAL(18,6)`；前端响应不得把精度要求改成浮点运算。
3. 检查新代码无 `toISOString()` 计算业务日期、无 `count >= 0`、无条件跳过、无“开发中”替代业务动作。
4. 检查 Flyway 已应用迁移未修改，输出 `git diff --name-only -- backend/src/main/resources/db/migration` 并核对只新增未改旧文件。

## 4. G1 构建与基础测试

执行第 2 节全部命令。后端当前预期是除 Docker/Testcontainers 兼容阻断外其余测试通过；前端单测和构建必须全绿。记录总数、失败数和错误栈，不得写“99.5%覆盖率”代替。

## 5. G2 数据与导入

使用正式模板导入经营计划、GOOD/DEFECTIVE 库存、BOM 快照、分类角色、物料策略、生产日历。

- 断言库存导入模板含中文表头“库存品质*”和单一“库存数量”列，不存在“各仓数量”多列；导入弹窗先选永惠/爱培科/惠州仓之一，GOOD 与 DEFECTIVE 分开保存，数据库按快照+仓库+物料+品质汇总。
- 断言缺失库存记录生成业务问题，不由系统静默猜成 GOOD=0；完成业务确认后才能计算。
- BOM NC 同步以库存组织+父料号+子料号为业务键，只保留最高有效版本；最高版本重复、有效期重叠、组织为空必须阻断快照并生成可处理错误。
- 查询 `bom_snapshot`、`bom_snapshot_detail`，确认计算只引用 READY 快照，不读取 NC 原表。

## 6. G3 黄金算法

准备整机 A 经营计划 1000、A 库存 100；机头 B 用量 1、库存 200；机头 C 用量 2、库存 300；B 的 PCBA D 用量 1、库存 200。执行预检、计算、日分配。

必须逐层核对：整机净需求 900；B 毛需求 900、库存抵扣 200、B 净需求 700；D 需求 700、库存抵扣 200、D 净需求 500；C 毛需求 1800、库存抵扣 300、C 净需求 1500。若策略在 B 停止，B 应作为末级且不再生成 D；若策略 EXPLODE，必须生成 D。

追加共享库存、分支库存不足、PCBA 无库存记录、外购/委外 STOP/EXPLODE 各一例，逐项核对问题类型和决策门禁。共享库存必须按经营计划 `priority_seq` 生成 `AUTO_PRIORITY`，不得因 Java/SQL 遍历顺序变化而改变结果。

## 7. G4 日排产与人工覆盖

用 28 天、84 天生产日历，包含周末和节假日。断言日事实按生产日分配，整月需求守恒，整体提前 7 个自然日；周/月仅由日事实聚合。

在一个未来未锁定日期保存人工数量 12.500000：

1. API 返回 `systemQuantity` 原值、`effectiveQuantity=12.500000`；
2. 周汇总、月汇总、守恒接口均使用 `effectiveQuantity`；
3. 恢复自动后有效值回到系统值，且系统值未被覆盖；
4. 锁定/执行日期保存覆盖必须返回业务错误。

检查日网格固定列为“来源整机、末级需求物料、终点/供应、工厂”，两列不可合并；日期为日列并按已发布业务周 W1-Wn 分组，不能出现来源周/实际周歧义。

## 8. G5 active run 与重算

对同一批次执行两次成功计算，记录旧、新 `run_id`，所有 requirements、terminal demand、daily plan 必须只属于 active run。接口和 SQL 不得按 `batch_id` 混读历史 run。

第三次注入一个确定失败（例如非法 BOM 数量或缺日历），断言：

- 失败 run 为 FAILED 或被清理；
- 旧成功 run 仍是 `active_run_id`；
- 旧 requirements、terminal demands、daily plans 数量和 checksum 不变；
- 批次不得进入 READY/PUBLISHED。

重复提交同一 `testRunKey` 必须幂等，不产生重复 issue、重复分配或重复日事实。

## 9. G6 库存分配、决策与发布

通过库存分配页面/接口验证系统自动生成的 `AUTO_PRIORITY`，再完成一次有原因的 `MANUAL_OVERRIDE` preview + commit。查询 `mrp_inventory_allocation` 和库存池余额，确认分配数量守恒、安全库存未被消耗、预警状态正确、并发超分配被拒绝、revision 冲突返回 409。

决策中心解决一个 OPEN issue，确认调用 resolve API、revision 校验生效、批次未解决数递减。发布前分别保留 OPEN issue、PENDING unallocated、未确认库存分配、守恒不平衡、STALE 输入，发布必须被阻断并返回明确错误。

发布成功后旧批次只读；修改只能生成修订批次。修订批次的需求、末级需求、日计划、问题、人工覆盖和执行追溯必须能按新 run 查询，不能只复制日计划。

## 10. G7 执行与月中重算

手工确认一个未来日期实际完成量，断言写入执行事实并生成差异异常（实际量不等于计划量）。再次确认同一 EXECUTED 日期必须失败；LOCKED/EXECUTED 日期不得被重算或人工覆盖。

月中重算只处理未来未锁定日期；执行日和锁定日数量、执行状态、异常记录保持不变。库存分配选择“保留原分配/重新分配”未明确时只能预览，不能发布。

## 11. G8 权限、兼容与接口

匿名访问返回 401；无权限用户返回 403；跨工厂访问返回 403 或空域数据，不能泄露其他工厂。逐端点检查批次、预检、计算、日计划、覆盖、库存分配、决策、执行、发布、修订。

Phase 1 `/planning` 页面和历史 API 只读兼容，Phase 2 不写旧周 override 表，不改变旧 checksum。

## 12. G9 浏览器强断言

Playwright 必须使用本次创建的批次，不得固定历史 ID。每个用例必须断言：URL、页面标题、表格列、真实 API 响应、按钮启用/禁用、成功后重新加载仍保留结果、空数据与 401/403/500 有明确 Element Plus 状态。

至少覆盖 1440x900、1920x1080、28 天、84 天；检查无横向布局重叠、控制台无新增错误。失败保存 trace 和截图，禁止条件跳过。

## 13. G10 性能与数据库

准备 10,000 条日明细、1,000 条 BOM 边。记录计算耗时、内存、SQL 次数、响应大小。确认没有逐行 N+1；日网格服务端分页只返回当前页；DECIMAL 精度在 JSON、Java、数据库往返不丢失。

## 14. 最终签收标准

所有 G0-G10 为 PASS，或仅有已书面记录且可复现的环境 BLOCKED；不得存在 P0/P1。`assets/MRP-UAT验收案例库.md` 每个相关案例必须逐条更新为 `已通过/失败/阻塞`，附证据路径。未满足任一项，Phase 2 保持 `REVIEW`，不得报告完成。
