# MRP Phase 2 交给 Mimo 的启动 Prompt

复制下方正文给 Mimo：

```text
你将负责完整实现 MRP Phase 2 多层净需求与日排产，必须使用仓库内 Harness 和多 Agent 流程。

第一步只执行 .harness/prompts/10-contract-migration.md。开始前完整读取：
1. AGENTS.md
2. project.md
3. .harness/execution-laws.md
4. .harness/README.md
5. .harness/runbook.md
6. .harness/source-precedence.md
7. .harness/technology-baseline.md
8. docs/MRP需求文档v4.0（Phase 2）.md
9. docs/MRP多层净需求与日排产开发手册.md
10. .harness/workflow.yaml
11. 当前阶段 Prompt 及其中引用的全部代码、业务文档和资产

强制约束：
- 先用 git status --short 记录工作区基线，保护所有用户现有改动；禁止 reset、checkout、clean、覆盖未提交文件；
- 每阶段开始由 Coordinator 发布 Agent 文件所有权表；同一文件只能一个写 Agent，迁移序号和共享契约必须独占；
- 00-09 是 Phase 1 历史 DONE，禁止修改状态冒充 Phase 2 进度；
- Phase 2 从 10 开始，阶段实现完成置 REVIEW；独立 Reviewer 执行 REVIEW-STAGE，FAIL 执行 REPAIR-GATE 并复核，PASS 才置 DONE；
- PASS 且无业务/环境阻塞时自动继续下一阶段，直到 19 和 FINAL-AUDIT 完成；
- 每个 Agent 在 .superpowers/sdd/2026-09-22-phase2-daily-mrp/ 写 task brief/report，Coordinator 维护 progress；
- 启动先枚举 Agent spawn、并行任务和独立 Reviewer 能力；若不可用，记录 degraded mode，不能把同一会话自审称为独立审查。没有另一会话或人工 Reviewer 时，涉及实现的阶段置 BLOCKED；
- 先枚举运行环境是否真实提供 Superpowers 技能/CLI。存在时记录真实能力名、版本、调用和输出；不存在时禁止伪造命令，使用 Harness Review/Repair/Final Audit 完成等价闭环；
- 不引入未批准中间件，不修改 Java/Node/Spring Boot 技术栈版本，不修改已应用 Flyway 迁移；
- 已出货本期 TODO，不进入公式、checksum、预检或发布；
- 日排产是唯一事实，周/月只读聚合；业务不确定项必须在计算前由业务工作台锁定，后台计算不得弹窗等待；
- 每次代码改动完成编译和自动测试；每阶段完成必须实际启动服务，用 curl 和浏览器验证；
- 环境不满足时严格使用 AGENTS.md 的环境问题格式停止，不能自行安装、切换或修改版本；
- 任何失败保存证据，做根因分析、最小修复、定向复测、全量回归和独立复核；阻断清零前不得报告完成。
- 业务已冻结 Q2-012、Q2-013、Q2-014、Q2-016，以及 Q2-015 的已执行/锁定日和月中重算规则：库存按快照+仓库+物料+`inventory_quality` 汇总（GOOD 参与、DEFECTIVE 排除）；工厂只扣减已发布映射的仓库；NC 按库存组织+父料号+子料号选最高有效 BOM 版本；MVP 不做单位换算；已执行/锁定日不可覆盖、月中只重算未来、发布后生成修订批次。
- 多整机共享库存按来源月+经营计划冻结顺序生成 `AUTO_PRIORITY`；安全库存是硬保底，预警库存为风险线；周汇总必须读取业务按月发布的 W1-Wn 日期范围，不得使用自然周推算。
- Q2-015 仅剩“月中重算后库存分配保留原分配还是重新分配”未决。阶段 15/16 必须实现明确的差异预览和发布阻断；不得自行选择。其他后续问题按 `assets/MRP待确认问题池.md` 执行。

开始阶段 10 前，先回复：已读取文件、真实代码差距、工作区保护策略、Agent 分工、将修改文件、业务不变量和验证方法，然后执行。
```
