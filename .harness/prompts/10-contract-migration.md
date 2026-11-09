# 阶段 10：契约迁移与真实代码基线

## 目标

冻结 Phase 2 的需求、数据、算法、API、页面和兼容策略，确认真实代码差距。此阶段原则上不写业务实现。

## 必读

`AGENTS.md`、`project.md`、`.harness/execution-laws.md`、`.harness/source-precedence.md`、v4.0 需求、完整开发手册、旧 v3.0、相关代码与原型。

## 执行

1. 记录 `git status --short`、最高 Flyway 版本、Java/Node/Maven/Docker 环境，只读保护用户改动。
2. 逐项验证现有经营计划、两套库存、BOM、周算法、任务状态、事务、API 和前端数据流。
3. 建立 `.superpowers/sdd/2026-09-22-phase2-daily-mrp/progress.md` 和 Agent brief/report。
4. Coordinator 划定阶段 11-19 文件所有权和共享契约。
5. 在实现前检查运行环境是否真的提供 Agent spawn/独立 Reviewer；可用则记录能力并启用，不可用不得伪装独立审查，改为 BLOCKED 或等待另一会话/人工 Reviewer。
6. 明确 `mrp_calc_batch` 是 Phase 2 唯一 plan/version 身份，日表只用 batch_id；冻结分析波次和 `mrp_calc_run` 分块写入契约。
7. 更新仍有冲突的需求/Harness 文档、追踪矩阵和 TODO；不得把 SPECIFIED 写成 VERIFIED。
8. 创建并评审 `prototype/MRP日排产Phase2可交互原型.html`，明确日事实和双料号列；只冻结契约，不复制原型代码。

## 门禁

- v4.0、contracts、decision-log、workflow、UAT 无互相冲突；
- 旧 Phase 1 文档明确标为历史；
- 所有未决项分为阻断决策或明确 TODO；
- 业务已冻结 Q2-012、Q2-013、Q2-014、Q2-016，以及 Q2-015 的执行日/锁定日规则；仅“月中重算后库存分配保留还是重新分配”仍是 P0 决策。阶段 10 不得把已冻结事项继续标为阻断，但必须把该剩余子项写入 decision request，并确保阶段 15/16 对受影响批次有发布阻断。
- 输出真实代码差距、迁移风险、所有权表和验证计划。

完成后置 REVIEW，独立执行 REVIEW-STAGE；PASS 后自动进入 11。
