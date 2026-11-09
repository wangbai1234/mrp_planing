# C022 业务周均分与整体前滚任务报告

## 状态

DONE。编译、全量测试、真实 HTTP、浏览器及本地服务更新均通过；独立 Reviewer 结论 PASS，见 `2026-10-19-business-week-rolling-review.md`。本报告只关闭 C022 变更任务，不改变 Phase 18/19 总体阶段状态。

## 本次完成

- 根据用户“理解准确”的确认，月净需求按来源月冻结的实际业务周数 N 均分，周余数全部放第一份。第 1 份排到上月最后业务周，第 2 至 N 份排到本月前 N−1 个业务周，本月最后业务周承接下月第一份；没有下月需求时不额外生成数量。
- 周内按本厂生产日均分，日余数从早到晚补齐，无额外减 7 自然日。例：四周月 1003 → 253/250/250/250；五周月 1003 → 203/200/200/200/200。
- 新建批次使用 `v5-shifted-business-weeks`；保留 v4 固定三份引擎分支及更早版本兼容。历史批次和从历史批次生成的修订版沿用原版本；要使用新规则，需要重新生成排产。
- 后端滚动查询、产能口径及前端联动调整入口识别 v5。日事实继续保存来源月，周/月汇总读取同一事实口径。
- 本地 8080 后端已按既有启动配置重启，前端构建已同步到后端静态资源。覆盖文件事前备份，既有资源块保留，未更改技术栈配置或数据库迁移。

## 修改文件

- 后端：`ProductionCapacityRules.java`、`DailyPlanningService.java`、`RollingPlanningQueryService.java`、`RollingBusinessWeekDistributionEngine.java`（均位于 `backend/src/main/java/com/mrp/mrp/` 下）。
- 前端：`frontend/src/views/mrp/MrpWorkspace.vue`、`components/RollingPlanningGrid.vue`、`components/DailyPlanningGrid.vue`。
- 自动化测试：`backend/src/test/java/com/mrp/mrp/engine/RollingBusinessWeekDistributionEngineTest.java`、`frontend/src/views/mrp/__tests__/RollingPlanningGrid.test.ts`。
- 契约与需求：`project.md`、`.harness/decision-log.md`、`.harness/source-precedence.md`、`.harness/calculation-contract.md`、`docs/MRP需求文档v4.0（Phase 2）.md`、`docs/MRP多层净需求与日排产开发手册.md`、`docs/MRP排产页面改版设计草案.md`。
- 交付证据：本报告、独立复核报告、`.harness/workflow.yaml` 对应任务、`test-evidence/business-week-rolling-2026-10-19/` 及前端构建静态资源。

## 业务不变量

- 上游净需求、库存扣减和 BOM 计算不变；只改变 v5 的月净需求分周目标与份数。
- 数量全程 BigDecimal；已排数量与待分配数量之和等于来源需求，工厂隔离、来源月可追溯。
- 分母采用冻结业务周；缺失或非法来源月周定义时整月保留待分配，缺目标周、日历不全或停产时保留对应份额，不自动改投其他周。
- 历史规则不被新版本改写，已执行/锁定、人工调整与历史版本保持原语义。
- 周按冻结业务周汇总，月按实际生产自然月汇总；不存在额外日期减 7。

## 验证命令与结果

证据目录：`test-evidence/business-week-rolling-2026-10-19/`。

| 验证 | 实际命令 / 操作 | 结果与证据 |
|---|---|---|
| 后端编译 | `cd backend && ./mvnw compile` | BUILD SUCCESS；`compile.log` |
| 前端编译与类型检查 | `cd frontend && npm run build`（内含 vue-tsc） | 通过；`frontend-build.log` |
| 后端全量测试 | `cd backend && ./mvnw test` | 559 tests，0 failures/errors/skipped；10:06 分钟；`backend-test.log` |
| 分周引擎 | 上述全量测试中的 RollingBusinessWeekDistributionEngineTest | 16 项通过，包括 7 项新增 v5 用例 |
| 前端全量测试 | `cd frontend && npm test` | 12 文件、81 项通过；`frontend-test.log` |
| 真实分配 API + v5 浏览器 | `python3 test-evidence/business-week-rolling-2026-10-19/runtime_check.py`，脚本调用 `browser_check.cjs` | PASS；`runtime-result.json`、`runtime-browser-verified.log`、`browser-result.json`、`v5-adjustment.png` |
| 本地 8080 更新 | `python3 test-kit/2026-10-16/tools/restart_verified_backend.py main`；同步既有前端构建 | 服务 19:28:08 启动；`static-refresh.txt` 记录备份位置 |
| 运行服务只读核对 | `python3 test-evidence/business-week-rolling-2026-10-19/main_refresh_check.py before` / `after` | PASS；静态首页与构建一致、实际提供 v5 资源、批次 103 守恒差异 0；`main-refresh-result.json` |
| 更新后浏览器 | 系统 Chrome / Playwright 访问 8080 的 `/planning/103`，切换日/周/月视图 | PASS；历史规则仍为 v4，页面错误 0；`main-browser-result.json`、`main-browser.png` |
| 独立复核 | 实际创建→冻结→分配→查询/汇总→调整→修订链路复核 | PASS；独立复核报告 |

真实 API 在隔离 MySQL / 8180 服务上使用三个可清理夹具：2026-11/12 的五周月、2027-02 四周与 03 五周承接，以及历史 v4。每份数量与冻结目标周 SQL 对账一致，全部守恒差异为 0，日/周/月接口成功，重复分配被 400 拒绝。v5 浏览器使用真实 API，验证日/周/月标签、虚拟表格、无旧版提示及联动调整弹窗可用，页面错误及失败 API 数均为 0。

所有临时夹具已清理，隔离来源批次 13 的日事实摘要不变。本地既有批次 91、103 的 run、月需求、日事实和 override 全列摘要在重启前后相同；两个批次各 224,100 条日事实、17,928 条需求，均未改写。

首次浏览器验收脚本修正过相对模块路径、API 拦截范围、控件定位与夹具创建时间，失败日志及截图保留；最终成功结果见以上文件。主服务首次只读检查使用隔离会话返回 401，保留 `main-refresh-initial.log`，重新使用主服务有效登录后通过；未改账号密码或权限。口令和 token 仅保存在 `/tmp` 私有文件，未写入报告或证据输出。

## 未完成与风险

- 本任务无未完成项。新规则不会自动重算已有排产；历史修订版也保持来源规则，需通过“生成排产”创建新批次应用 v5。
- 前端构建存在既有大资源块提示，不影响本次构建和浏览器验收；未扩大范围调整构建配置。
- 工作区存在大量先于本任务的修改与未跟踪文件，未重置、清理或宣称其全部属于本任务；整体 Phase 2 后续门禁仍按原工作流处理。

## 决策/待确认

C022 已由用户确认并记录，无新增业务待确认项。无新增中间件、依赖、API 结构或数据库迁移。

## 下一步

用户下次“生成排产”将使用 v5 业务周规则。
