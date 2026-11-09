# MRP Phase 2 第三轮修复执行 Prompt

你是 Mimo 的实现协调 Agent。请先读取 `project.md`、`.harness/execution-laws.md`、`.harness/README.md`、`.harness/workflow.yaml`、`docs/MRP需求文档v4.0（Phase 2）.md`、`docs/MRP多层净需求与日排产开发手册.md`，再完整阅读 `docs/MRP Phase2第三轮验收报告与修复手册.md`。

目标：按报告修复当前 Phase 2，直到满足通过标准。可以拆分给多个 Agent 并行，但必须先声明互斥文件边界；迁移文件、共享 DTO、SecurityConfig、workflow 同时只能由一个 Agent 修改。数据库变更只能新增 V028+，禁止修改 V001-V027。

执行重点：

1. issue 阻断与 active run 隔离；
2. Worker lease/heartbeat/retry、当前 run 失败清理和批次输入绑定；
3. GOOD/DEFECTIVE、缺库存决策、组织维度、共享库存分配和 BOM 最高有效版本裁决；
4. 执行 rowKey、LOCKED/EXECUTED 保护、数量差异异常、月中重算和独立修订版本；
5. MRP 细粒度权限、工厂数据域、DECIMAL 字符串契约；
6. 日排产工作台、周/月只读汇总、决策/库存/日历/执行页面和 Phase 2 Playwright/UAT。

每个 Agent 完成后必须提交：修改文件、业务不变量、测试命令、真实输出、未决项和 Reviewer 结论。按仓库铁律依次执行编译、单元测试、真实 API/浏览器联调；不能以排除测试、静态构建或历史报告代替全量验收。Docker/Testcontainers 不可用时明确记录环境阻断，不得修改技术栈或伪造 PASS。

最终只在以下条件同时满足时报告完成：后端全量测试通过、前端测试和构建通过、Phase 2 API/浏览器/UAT 有证据、失败 run 不破坏旧结果、LOCKED/EXECUTED 日保护和发布修订通过、10-19 阶段由独立 Reviewer PASS。否则保持 `REVIEW` 并输出剩余阻断项。
