# 阶段 16：API、异步任务、事务与发布

## 目标

连接预检、决策、计算、结果写入、查询、调整和发布，形成后端闭环。

## 必读

`api-contract.md`、`task-execution-contract.md`、`data-contract.md`、`security-and-ops.md`、开发手册第 7/9 节。

## 执行

1. 实现 batch/precheck/issues/allocation/lock-and-calculate/task/result/publish API；单一批次通过 `mrp_batch_factory_scope` 覆盖多工厂范围。
2. 创建任务使用 Idempotency-Key；认领使用 MySQL lease/SKIP LOCKED 和 Redisson 业务锁。
3. 实现 `ANALYZING -> WAITING_DECISION -> REANALYZING` 波次；共享库存按来源月+经营计划优先级生成 `AUTO_PRIORITY`，人工覆盖、缺配置或非生产日才进入 WAITING_DECISION；所有波次稳定且 checksum 锁定后进入 READY_TO_CALCULATE。
4. Orchestrator 不持有长事务；MrpResultWriter 使用 `mrp_calc_run` 分块短事务写入，最后以短事务切换 `active_run_id` 并置 READY。
5. 失败、取消、重试、心跳、恢复和 STALE 符合状态机。
6. 发布前校验所有 issue、待分配、生产日历、业务周覆盖、工厂仓库映射、安全库存保底、守恒和 checksum。
7. 已出货字段不进入任何 Phase 2 判断。
8. 发布后采用修订版本：已发布批次不可原地修改；新修订批次关联前序批次，保留旧版本，只允许未来未锁定日期变更并重新走预检、计算和发布门禁。
9. 月中重算后的库存分配保留/重新分配仍是唯一业务阻断项；未决时实现差异预览和明确阻断，不得自行选择。

## 门禁

- 幂等、双 Worker、租约恢复、409、Writer 注入失败回滚；
- READY/PUBLISHED 不被覆盖；
- WRITING/FAILED run 对查询不可见，中途失败不留下 READY 半版本；
- 401/403/工厂数据域；
- compile/test + 实际启动 API/Worker + curl 完整闭环。

完成后 REVIEW，事务/并发 Reviewer PASS 后进入 17。

## C025 两阶段交接增量

在既有任务、事务和发布门禁上，先完成根整机净算/候选日事实，再以同一根净需求和排产 revision 计算末级需求。确认记录绑定 batchId、activeRunId、排产 revision、结果 checksum；整机调整或重算使物控确认失效。发布需双岗当前指纹确认，并保留全部既有门禁。新增物料反馈单及按角色分离的写权限，不改变已发布历史。
