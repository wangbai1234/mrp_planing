# MRP 实际计算进度修复报告

## 状态

REVIEW：代码、完整回归与运行验证完成，等待独立阶段复核。

## 本次完成

- 核实主环境 `/api/v1/mrp/batches/96/calculation-task` 的真实错误为 MySQL `Out of sort memory`。旧查询使用 `SELECT * ... ORDER BY created_at DESC,id DESC`，把大 JSON 输入载荷带入排序；请求失败导致前端保留 0% 并显示“进度更新重试中”，直至 READY 才显示 100%。原 Worker 的进度阶段也只有少数几个固定值。
- 最新任务查询改为内层只排序任务 ID，外层只读取状态、进度、阶段和错误字段，不读取输入载荷，不调整数据库配置。
- BOM 展开和库存扣减按完成的整机需求统计；日分配按完成的需求组统计；物料需求、末级需求和日排产写入按实际完成的分块数量统计。页面显示阶段、百分比及完成数/总数。
- 进度使用独立短事务提交，计算长事务尚未完成时也能被页面读取。写入校验 RUNNING、worker 和认领版本，不回退进度，不覆盖终态；100% 保留给最终任务完成。
- 同一阶段最多每秒持久化一次且只在百分比改变时写入；阶段切换立即写入。前端保持两秒轮询，不使用计时器伪造百分比。进度写入异常不会回滚有效业务计算。
- 成功返回空进度不再显示失败重试，已完成时不再附加“重试中”。

## 修改文件

- `backend/src/main/java/com/mrp/mrp/engine/CalculationProgressListener.java`
- `backend/src/main/java/com/mrp/mrp/engine/{NetRequirementEngine,RollingBusinessWeekDistributionEngine,DailyDistributionEngine}.java`
- `backend/src/main/java/com/mrp/mrp/service/{TaskProgressService,MrpTaskWorkerService,MrpCalculationService,DailyPlanningService}.java`
- `backend/src/main/java/com/mrp/task/repository/CalcTaskMapper.java` 与对应 XML。
- `frontend/src/views/mrp/{MrpWorkspace.vue,components/CalculationProgress.vue}` 与进度组件测试。
- 新增 `TaskProgressServiceTest`、`TaskProgressIntegrationTest`，更新 Worker 相关测试的构造与服务调用。
- `test-kit/2026-10-19/e2e/actual-progress.spec.mjs`、专用 Playwright 配置、`verify-progress-results.py` 及 evidence 证据。

## 业务不变量

- 库存扣减、BOM 用量、跨月三份滚动、工厂隔离、日分配和数量守恒公式不变。
- 监控失败不改变计算结果；过期 Worker 不能覆盖其他认领或终态任务的进度。
- 没有更改迁移、技术栈版本、主环境排产结果以及物料、分类、BOM 主数据。
- 主环境批次 #96 保持 READY；隔离环境当前批次 #13 保持 PUBLISHED。

## 验证命令与结果

```text
cd backend && ./mvnw compile
PASS；日志 /tmp/mrp-progress-compile.log

cd backend && ./mvnw test（进度、Worker、分块与分配引擎专项）
35 项通过，0 failures / errors / skipped
日志 /tmp/mrp-progress-targeted-test.log

cd backend && ./mvnw test
548 项通过，0 failures / errors / skipped，BUILD SUCCESS，耗时 12:59
日志 /tmp/mrp-progress-full-test.log
摘要 test-kit/2026-10-19/evidence/actual-progress-full-test-summary.txt

cd frontend && npm run build
PASS；日志 /tmp/mrp-progress-frontend-build.log

cd frontend && npm test -- --run
12 个文件、74 项通过；日志 /tmp/mrp-progress-frontend-test.log

python3 test-kit/2026-10-16/tools/restart_verified_backend.py isolated
python3 test-kit/2026-10-16/tools/restart_verified_backend.py main
前端静态产物部署、双后端实际重启，8080 / 8180 health 均 UP

Playwright --config=test-kit/2026-10-19/e2e/playwright-progress.config.mjs
1 项端到端测试通过，实际完成两次排产；日志 /tmp/mrp-progress-browser.log

python3 test-kit/2026-10-19/verify-progress-results.py
PASS，修复前后四张结果表逐字段等价
```

MySQL 专项验证了：外层事务提交前其他线程即可读取进度；外层回滚后进度仍保留；过期 worker/version、终态、进度回退受保护；六条约 400KB 输入载荷、32KB 会话排序缓冲下最新任务查询成功，响应不含输入载荷。

实际浏览器验证：

| 隔离批次 | 日事实行数 | 观测百分比种类 | 守恒差异 | 进度接口中位/最大耗时 |
| --- | ---: | ---: | ---: | --- |
| #55 | 38,850 | 17 | 0 | 27 / 64 ms |
| #56 | 219,450 | 30 | 0 | 27 / 488 ms |

页面实际出现“80% 保存日排产（10000/38850）”“84% 保存日排产（110000/219450）”“96% 校验排产数量守恒”等文字；两次都最终显示 100% 计算完成。无浏览器脚本错误、无进度接口 HTTP 错误、无崩溃。#55 / #56 已取消并保留计算事实，未发布。

相同输入的修复前 #52 与修复后 #55 比较：库存池 434 行、物料需求 3,048 行、末级需求 3,108 行、日事实 38,850 行，排除身份/审计时间字段后其余字段全部相同、SHA256 相同。

主环境 #96 进度接口连续八次只读调用均 200，15.19–40.53 ms，响应 183 字节。最终 health/current 检查见 `actual-progress-final-health.json`。

证据目录：`test-kit/2026-10-19/evidence/` 中 `actual-progress-runtime.json`、`actual-progress-exact-comparison.json`、`actual-progress-main-readonly.json`、`actual-progress-final-health.json` 和 `actual-progress-browser-results/`。

## 未完成与风险

- 百分比按阶段划分区间，再按该阶段的实际处理数量更新，表示已完成工作，不是剩余耗时预测。任务很快时，两秒轮询可能跳过短阶段。
- 进度监控有少量额外短事务和查询；已节流，未对每个 BOM 节点查库。大数据实测完成证明本次规模可运行；没有同等空闲条件下的严格 A/B 耗时对比，不能声称零开销或任意规模不会变慢。
- 完整回归与大数据测试同时运行，耗时不可用作严格的前后性能对照；大数据新结果与历史夹具行数也不同，不作精确性能增减结论。
- 首次大数据夹具 #54 因 25 条 INVENTORY_MISSING 停在待决策，并非进度异常。保留首败日志和现场，取消该批次；正式回归 #56 通过既有接口人工确认这 25 条缺失库存按零处理，没有改库存或主数据。所有决策保留在 runtime 证据。普通规模首次 #53 成功证据亦保留。
- 前端已有构建 chunk 大小和 Vue emits 提示仍存在。整体性能、安全和 Worker 租约恢复等既有开放问题不因本补丁关闭。

## 决策/待确认

无新增业务决策；沿用用户授权修复实际进度显示。

## 下一步

独立阶段复核；通过后再将该子项转为 DONE。
