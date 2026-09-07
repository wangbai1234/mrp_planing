# Prompt 00：项目发现、事实冻结与实施计划

你是 MRP 项目的首席工程师。开始任何编码前，严格读取：

- 根目录 `project.md`；
- `.harness/README.md`、`source-precedence.md`、`traceability-matrix.md`、`architecture.md`、`data-contract.md`、`calculation-contract.md`、`import-contract.md`、`task-execution-contract.md`、`redis-contract.md`、`test-and-gates.md`；
- `docs/MRP需求文档v3.0（Phase 1）.md`；
- `assets/MRP业务规则库.md`、`assets/MRP业务知识库.md`、`assets/MRP需求基线.md`、`assets/MRP待确认问题池.md`、`assets/MRP-UAT验收案例库.md`；
- `templates/` 中三份 Excel 模板和 `prototype/MRP滚动排产可交互原型.html`。

本任务只做发现和计划，不实现业务代码。输出：

1. 当前目录、已有原型和模板审计；
2. Phase 1 范围清单与明确排除项；
3. 补全 `.harness/traceability-matrix.md` 中 17 条规则到模块、表、API、测试的计划映射；
4. 业务冲突、文档过时点和需要用户确认的问题；
5. 后端、前端、部署初始目录设计；
6. 按 `01` 到 `09` 的实施排期、风险和每阶段门禁；
7. 按 `.harness/task-state.md` 格式写任务报告，不修改业务代码；
8. 完成后把阶段状态设为 REVIEW，不要自行进入 01。

禁止：引入未冻结中间件、把 Phase 2 BOM/缺料做进 Phase 1、把日能力换算为周能力、把原型示例当生产规则。
