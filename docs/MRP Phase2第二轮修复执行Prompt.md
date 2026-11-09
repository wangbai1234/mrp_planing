# Mimo 执行提示词

请对当前 MRP Phase 2 工作区执行第二轮修复和验收，完整依据：

`docs/MRP Phase2第二轮验收报告与修复手册.md`

要求：

1. 先读取 `AGENTS.md`、`project.md`、`.harness/execution-laws.md`、`.harness/README.md`、`source-precedence.md`、当前任务 Prompt、Phase 2 需求/开发手册、原型和本修复手册；保存 `git status --short` 基线，保护所有用户/Mimo 未提交改动。
2. 使用多 Agent 并行，先声明互斥文件所有权；迁移、共享 DTO、SecurityConfig、workflow 只能由一个 Agent 修改；完成后必须由独立 Reviewer 复核。
3. 严格按手册顺序修复 P0-201 至 P0-209，再修 P1；不修改已应用 V001-V025，不使用 `flyway repair`，需要数据库变更只能新增 V026+。
4. 不得排除 `BomExplosionServiceTest`、删除测试、降低断言、伪造 UAT 或把同步调用标成异步；环境问题按 AGENTS.md 停止并报告，不修改 Java/Node/框架版本。
5. 实现后必须完成真实闭环：库存 GOOD/DEFECTIVE、缺库存决策、BOM 版本冲突、逐层扣减、共享库存分配、异步 task、日排产、执行锁定、月中重算、发布修订、权限和工厂域。
6. 最终运行并保存证据：

```bash
git diff --check
cd backend && ./mvnw clean compile && ./mvnw test
cd ../frontend && npm run build && npm test -- --run && npx vue-tsc --noEmit
```

另行在独立 MySQL 空库和 V020 升级副本执行迁移验证，启动真实 API，使用 curl 和 Playwright 验证 401/403、批次全流程、库存品质、BOM 冲突、异步 task、日网格、周/月汇总、执行确认、修订和发布阻断。

完成后更新受影响阶段报告、`workflow.yaml` 和 issue/decision 记录，保留失败证据；只有独立 Reviewer PASS 后才可置 `DONE`。最终报告必须列出修改文件、命令退出码、测试数量、数据库迁移 history、API 响应、浏览器/E2E 证据和剩余 TODO。
