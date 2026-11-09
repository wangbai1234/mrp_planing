# MRP Harness 文档索引

`.harness/` 是 Mimo 执行本项目时的流程控制层。`project.md` 是总入口；本目录文档负责把业务规则转成可执行的工程约束。

## 读取顺序

1. 根目录 `project.md`；
2. `.harness/execution-laws.md`（执行铁律，每次会话必读）；
3. 当前任务对应的 `.harness/prompts/*.md`；
4. 任务引用的契约文档；
5. `docs/MRP需求文档v4.0（Phase 2）.md` 和 `docs/MRP多层净需求与日排产开发手册.md`；
6. `assets/MRP业务规则库.md`、`assets/MRP业务知识库.md`、`assets/MRP-UAT验收案例库.md`；
7. 需要时读取 `.harness/decision-log.md` 和 `.harness/task-state.md`；
8. 发生历史文档冲突时读取 `source-precedence.md`。v3.0 和 00-09 报告只作 Phase 1 历史证据。

## 文档地图

| 文档 | 用途 |
|---|---|
| `execution-laws.md` | 执行铁律：三层验证、原型驱动、Spec 先行、前端联调、DB 迁移（不可跳过） |
| `architecture.md` | 模块边界、运行形态、依赖约束 |
| `data-contract.md` | MySQL实体、版本、状态和数据一致性 |
| `calculation-contract.md` | 排产公式、拆周、锁定、可重复计算 |
| `import-contract.md` | Excel解析、staging、校验和确认导入 |
| `api-contract.md` | API资源、错误格式、幂等和分页 |
| `security-and-ops.md` | JWT、权限、日志、配置、备份和监控边界 |
| `task-execution-contract.md` | 无 MQ 条件下的任务认领、心跳、重试和恢复 |
| `redis-contract.md` | Redis key、TTL、锁、失效和降级规则 |
| `test-and-gates.md` | 测试策略、性能基线和阶段门禁 |
| `reports/stage18-19-five-hour-acceptance.md` | 阶段 18/19 的 300 分钟单轮验收、119 项覆盖映射和证据复用规则 |
| `source-precedence.md` | 需求来源优先级和已裁决冲突 |
| `traceability-matrix.md` | Phase 2 规则到实现与测试的证据矩阵 |
| `code-standards.md` | Java、MyBatis SQL、Vue和测试约束 |
| `change-control.md` | 变更分类、影响分析与审批规则 |
| `technology-baseline.md` | 运行中间件与应用内库的边界 |
| `handoff.md` | 跨会话交接与上下文恢复规则 |
| `issue-log.md` | P0-P3 问题台账 |
| `task-state.md` | 任务状态、报告模板和阻塞规则 |
| `workflow.yaml` | 机器可读的阶段依赖、状态和报告路径 |
| `runbook.md` | 如何逐步把 Prompt 交给 Mimo |
| `prompts/` | 按阶段交给 Mimo 的可复制 Prompt |
| `templates/` | 实施计划、任务报告和决策请求模板 |
| `reports/` | Mimo 每阶段的实际执行报告 |

## Harness 工作方式

Harness 不要求 Mimo 每一步提交 Git commit，但要求每一步产生可检查的文件和验证证据。Phase 2 使用 10-19 阶段；每阶段实现后必须由独立 Reviewer 复核，失败执行 REPAIR-GATE，PASS 后可自动进入下一阶段。多 Agent 必须由 Coordinator 分配互斥文件所有权。

`.superpowers/sdd/` 是任务 brief/report/progress 证据目录，不代表仓库内存在可调用的 Superpowers 命令。运行时只有在真实枚举到能力后才能调用，否则使用 Harness Review/Repair/Final Audit 完成等价闭环。
