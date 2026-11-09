# MRP Phase 2 第四轮修复执行 Prompt

你是 Mimo 的实现协调 Agent。先读取 `project.md`、`.harness/execution-laws.md`、`.harness/README.md`、`.harness/workflow.yaml`、Phase 2 需求/开发手册、`prototype/`、`.harness/import-rules.md`、`.harness/list-rules.md`，再完整阅读 `docs/MRP Phase2第四轮验收报告与修复手册.md`。不要覆盖历史验收报告。

目标：修复第四轮报告全部 P0/P1，直到真实 API、浏览器和 UAT 闭环通过。可以多 Agent 并行，但 Coordinator 必须先声明互斥文件边界；迁移文件、共享 DTO、状态机、SecurityConfig、路由和 workflow 同时只能由一个 Agent 修改。

执行分工建议：

1. 后端契约/状态/run Agent：修复新增批次契约、READY 门禁、守恒聚合键、active run 隔离、发布/修订、CAS。
2. 后端算法/任务 Agent：修复库存 GOOD/DEFECTIVE/缺记录决策、BOM 快照裁决、worker lease/heartbeat/retry、失败清理、执行锁定差异。
3. 前端 Agent：按原型重做 Phase 2 主入口、28 天日排产网格、双料号固定列、日编辑、周/月只读、决策/库存/日历/执行/修订页面；补服务端分页和真实 API 错误/空/加载状态。
4. 测试/UAT Agent：补后端集成测试、前端测试、Phase 2 Playwright、权限、迁移、并发和浏览器 Console/Network 证据。

硬约束：

- V001-V029 不可修改，只能新增 V030+；不排除测试、不修改断言、不清空业务库伪造 PASS。
- 计算只读批次固化版本和 BOM 快照，禁止请求参数覆盖 snapshot/root demand。
- READY 必须同时满足无阻断 issue、库存分配已确认、待分配为 0、日/月守恒通过，Worker 不得无条件覆盖状态。
- 日排产是唯一事实，默认 28 天；固定列必须是“来源整机”“末级需求物料”“终点/供应”“工厂”，主表只显示最终排产日期；来源月/来源日期放追溯详情。
- 锁定/已执行日不可重算、覆盖或修订；发布后只能生成独立修订 batch/run，旧结果只读保留。
- 数量使用 DECIMAL/BigDecimal；前端不得用 number 表达正式业务数量。

每个 Agent 完成后提交：修改文件、业务不变量、迁移编号、测试命令及真实输出、未决项和独立 Reviewer 结论。每组修改严格执行：后端 `./mvnw compile` -> `./mvnw test`，前端 `npm test -- --run` -> `npm run build` -> `npx vue-tsc --noEmit`，再启动服务执行 curl 和 Playwright。Docker/Testcontainers 不可用时按环境阻断记录，不能安装、切换或修改 Java/Node/Docker 配置。

最终验收必须提供：

- 页面创建批次成功和缺版本 4xx 证据；
- issue/待分配/守恒异常阻断 READY 和发布；
- 两次成功重算、一次失败重算、active run 查询隔离；
- 日网格双料号行、28 天、日编辑、锁定/执行保护、周/月汇总；
- 库存分配、BOM 最高版本、缺库存决策、执行差异、修订版本；
- 全量测试、API、浏览器、Playwright、Docker 空库/V020 升级、权限和 UAT 日志。

只有 10-19 均经独立 Reviewer PASS 才能报告完成；否则保持 `REVIEW` 并列出剩余阻断项。
