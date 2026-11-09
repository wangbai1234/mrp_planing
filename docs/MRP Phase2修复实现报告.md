# MRP Phase 2 修复实现报告

## 状态

REVIEW。当前不能签收 Phase 2。

## 本轮完成

- 日计划保留 `systemQuantity`，增加 `effectiveQuantity` 投影；人工覆盖不再污染系统事实，日查询、周汇总、月汇总、守恒和日网格使用有效值。
- 日网格详情按批次 `activeRunId` 查询，避免历史 run 串读。
- EXECUTED 执行事实不可重复覆盖；锁定日期仍拒绝覆盖。
- BOM 快照读取不再在 SQL 首先过滤非法数量，后续校验可以真实发现非法数据。
- 库存分配、决策解决、修订历史前端按钮改为调用真实 API；分配流程执行 preview + commit。
- 新增逐项测试手册，禁止弱断言和条件跳过。

## 验证结果

```text
backend ./mvnw compile -q: PASS
frontend npm run build: PASS
frontend npm test -- --run: PASS (2 files, 15 tests)
backend ./mvnw test -q: BLOCKED/FAIL (188 tests; Testcontainers BomExplosionServiceTest 因 Docker API 400；其余本轮错误已修复)
git diff --check -- backend frontend: PASS
```

## 仍阻断的真实问题

1. Testcontainers 1.20.4 与当前 Docker Desktop socket 返回 400，必须由 Mimo 在不改版本、不排除测试的前提下完成环境兼容验证。
2. 失败重算 active run 保留、发布门禁完整性、修订事实全量复制、库存组织聚合、服务端分页和完整日网格仍需按测试手册验证并继续修复。
3. 已出货逻辑按已确认范围保留 `TODO-SHIPMENT`，不得加入本轮公式或发布门禁。

## 下一步

将 `docs/MRP Phase2修复后逐项测试执行手册.md` 交给 Mimo，按 G0-G10 顺序逐项执行。Mimo 测试完成后提供实际证据和失败用例，我再基于失败现场继续修复。
