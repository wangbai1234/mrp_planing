# Prompt 04：实现 Spring Batch 排产计算引擎

前置：导入和主数据测试通过。

读取 `calculation-contract.md`、`task-execution-contract.md`、`redis-contract.md`、`code-standards.md`、`mrp-ui-design/references/planning-contract.md`、UAT 案例和规则库。实现领域纯函数和 Spring Batch Job：

1. 按同一组输入版本读取 Forecast、库存、CRM 出货和周产能；
2. 计算 `MAX(0, forecast - inventory - shipped)`；
3. 按提前量 + 前三周拆分，余数前置；
4. 记录 physical month、source month、slot、isCarry；
5. 月中重算锁定已执行周，只重算未执行周；
6. 保留人工覆盖并分离自动值/人工值/最终值；
7. 做周产能超限校验，不自动搬移；
8. 使用 MySQL 任务表认领任务，Redisson 锁防重复；
9. 分片批量读取和 500–2000 行批量写入；
10. 计算成功才把 plan_version 标为 READY，失败保持当前版本不变。

测试必须覆盖黄金数据、跨年、库存大于需求、余数、承接周、月中锁定、人工覆盖、产能超限、重复计算 checksum 一致。禁止把公式放在 Controller 或 Vue 中。完成后状态设为 REVIEW，等待复核。
