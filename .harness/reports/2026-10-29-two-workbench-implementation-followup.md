# 双工作台缺料清单与运行验收续报

## 状态
REVIEW。用户已确认方案；代码及局部回归通过，仍待完整 v5 业务批次端到端验收、Oracle 健康门禁和独立复核。

## 本次完成
- 追加 V061 工厂归属字段，使无仓库号的策略风险也能按工厂隔离；新风险生成和发布复制均携带工厂，防止同料号跨厂误计。已应用旧迁移未改动。
- v5 缺料默认汇总沿同一 active run 按工厂、末级物料、终点类型、供应方式合行；库存池按原批次冻结池单独分页。来源月筛选只改变需求，不重复扣库存。v4 及更早版本保留原行粒度。
- 修正无仓库号的 `STOCK_POLICY_MISSING` 风险未进入汇总的缺陷，补真实 MySQL 多仓/双来源月测试；缺料页风险提示改为涵盖仓库风险及策略缺失的“库存风险”。
- 把旧 Playwright 用例对齐已确认的首屏精简布局，继续验证独立缺料工作台、交接入口和数据口径弹窗。
- 采用旧 8180 进程的环境变量重启后端，5180 Vite 保持原代理；未修改技术栈、已应用 Flyway 迁移或现有业务批次；新增的 V061 已在隔离库生效。

## 修改文件
- `backend/src/main/resources/db/migration/V061__stock_risk_factory_scope.sql`、`backend/src/main/java/com/mrp/mrp/domain/MrpStockRisk.java`、`backend/src/main/java/com/mrp/mrp/service/StockRiskService.java`、`backend/src/main/resources/mapper/mrp/MrpStockRiskMapper.xml`
- `backend/src/main/resources/mapper/mrp/MrpTerminalDemandMapper.xml`
- `backend/src/test/java/com/mrp/mrp/ShortageSummaryIntegrationTest.java`
- `frontend/src/views/mrp/ShortageWorkspace.vue`、`frontend/e2e/phase2-mrp.spec.ts`
- `.harness/issue-log.md` 与本报告

## 业务不变量
- 整机和末级数据共用一个 batch/active run；跨来源月仅汇总展示，不改事实主键、计算公式、发布门禁。
- 无仓库号的策略风险归属相同 run 和物料；有仓库号的风险必须匹配该工厂的库存池，避免跨工厂混入。
- 旧 v4 批次不冒充 v5 汇总和双岗确认；#73/PUBLISHED/run #67 实际规则为 v4。

## 验证命令与结果
```text
backend: ./mvnw -q compile => 0
backend: V061 后 ./mvnw -q test => 0；Surefire 92 suites / 642 tests / 0 failure / 0 error / 0 skipped
frontend: npm run build => 0；npm test -- --run => 12 files / 103 tests passed
frontend: npx playwright test e2e/phase2-mrp.spec.ts e2e/two-workbench-compact.spec.ts --project=chromium --workers=1 --reporter=line => 8/8 passed on 5180
8180: /actuator/info => 200；未登录 /api/v1/mrp/batches/current => 401
5180→8180: 管理员登录及批次 #73 shortage/page => 200，2304 条旧版行粒度；同批次 shortage/pools => 200，真实物料库存池 1 条；factories => 200，两个工厂；未登录 shortage/page 与 shortage/pools => 401
8180: /actuator/health => 503/DOWN，约 30 秒；OracleHikariPool 无可用连接超时，不能算健康门禁通过
8180: Flyway 成功由 060 升至 061；5180→8180 的 rolling-grid => 200、20 行；V061 重启后浏览器 8/8 再次通过。
git diff --check -- 相关文件 => 0
```

## 未完成与风险
- 隔离库当前 #73 是 v4 已发布批次，另有旧 v4 草稿。没有擅自作废或发布业务版本；真实 v5 整机确认→物料分析→物控复核→发布链路尚未在独立数据集完整验收。
- Oracle 健康探针持续 503；MySQL、Redis 和业务 API 可用，不等同整体健康。
- `SPLIT-UX-04` 的历史 2026-10-29 `11,255 / 10` 只证实数值在旧快照出现；用户所指工厂及组成没有可靠批次映射，不能宣称业务负荷合理。
- 阶段 16/17/18/19 继续 REVIEW；待独立 Reviewer 审查后才可置 DONE。

## 下一步
- 使用专用 v5 隔离批次，验证日负荷组成、双岗位完整流程与历史发布快照；排查 Oracle 数据源可达性与健康探针。

## 5180 恢复后续核验（2026-10-29）
- 核对当前 `RollingPlanningController → RollingPlanningQueryService → RollingPlanningMapper.selectMachineLoads → DailyCapacityService.evaluateMachineOutput`：整机负荷使用同工厂同生产日的有效整机事实求和；冻结日产能来自批次引用的生产日历版本。v5 排产表默认按工厂＋整机合行，历史 v4 仍保留来源月行。
- 对隔离 MySQL `mrp_testkit` 只读查询历史批次 #60/run #54（`v4-rolling-business-weeks`）：2026-10-29 永惠和爱培科各有 67 条 `MACHINE_OUTPUT` 日事实，分别对应 67 个整机，`system_quantity` 各合计 11,255 台；均归属来源月 2026-11，且 #60 的整机人工覆盖记录为 0。冻结日历版本 #30 中，两厂当日均为生产日、产能 10 台/日，因此各超出 11,245 台。经营计划版本 #18 在 2026-11 两厂各有 67 条、各合计 67,262 台。数值可按历史原始日事实守恒复现，但 10 台/日与计划量显著不匹配，业务仍需确认输入与配置口径；用户截图未提供可证明是 #60 的批次/run/工厂身份，不能把本次复核套用到截图。
- 当前运行只读检查：8180 `/actuator/info` 200、`/actuator/health` 200/UP，5180 `/planning` 和 `/shortage` 均 200；5180 实际 Chromium 双工作台回归重新执行 8/8 通过。此前报告中的 Oracle 健康探针 503 为当时状态，恢复后的健康 200 不证明 Oracle BOM 同步源可用。未执行新代码改动、业务写入或重新发布；编译、646/646 后端单测及 103/103 前端单测沿用上段已记录证据，不把本次只读查询冒充重新运行的门禁。
- 仍为 REVIEW：专用 v5 完整业务数据联调与独立 Reviewer 复核尚缺，Oracle 同步源另行排查。

## 产能日格语义修复与验证（2026-10-29）
- 按已读双工作台原型与现有查询链，仅调整 v5 与历史整机负荷固定行的显示：日格将已排负荷和冻结日产能分行列出；超限时明确列出“超出 N 台”。发布预警表也将“已排负荷”和“冻结日产能”分列，保留原有效事实、计算、发布确认与历史规则。同步更新双工作台原型和前端定向测试；方案文档补入 #60 的只读 SQL 结果。
- 修改：`frontend/src/views/mrp/components/RollingPlanningGrid.vue`、`frontend/src/views/mrp/MrpWorkspace.vue`、`frontend/src/views/mrp/__tests__/RollingPlanningGrid.test.ts`、`prototype/MRP整机排产与物料缺料双工作台原型.html`、`docs/MRP双工作台流程与页面精简优化方案-2026-10-29.md`、本报告和问题台账。
- 前端 `npm run build` 退出 0，`npm test -- --run` 为 104/104；5180 上 `npx playwright test e2e/phase2-mrp.spec.ts e2e/two-workbench-compact.spec.ts --project=chromium --workers=1 --reporter=line` 为 8/8。另以 Chromium 实际登录打开 `/planning/60`，日格读到“已排 11,255 台 / 日产能 10 台 / 超出 11,245 台”，两厂各一格。`git diff --check` 无空白错误。后端全量测试正在执行，不提前标记通过。
- 本轮只改前端与文档，后端与业务数据未改动。此运行样例为历史 v4；v5 双岗创建、物料计算、复核与发布的真实 API/Worker 闭环仍未验收，任务保持 REVIEW。

## 产能日格续验（2026-10-29）
- 上述前端改动后的后端全量测试最终完成：backend/./mvnw -q test 退出 0；Surefire 92 suites / 646 tests / 0 failure / 0 error / 0 skipped。
- 复核时 8180 /actuator/info 与 /actuator/health 均为 200；5180 /planning 与 /shortage 均为 200；git diff --check 退出 0。健康恢复不证明 Oracle BOM 同步源可用。
- 历史 v4 批次 #60 的日格与浏览器已验；专用 v5 真实双岗 API/Worker/发布闭环和独立复核仍缺，维持 REVIEW。

## 独立复核 FAIL 后的修复（2026-10-29）
- Reviewer 指出三个 P1：跨厂同物料待办串计且 issue key 冲突；V061 升级前风险无工厂归属漏显；多仓中仅部分仓有阈值配置时缺策略风险漏报。保持 REVIEW，未将首轮 FAIL 改写为 PASS。
- 新增 V062，仅为决策问题增加可空 factory_code 及索引，不修改已应用 V061。新问题记录工厂；v5 多工厂批次按工厂和原异常维度生成稳定有界键，单厂及旧 v4 保留原键。汇总仅挂本厂待办；旧 NULL 问题和风险仅当同 run 此物料只涉及一个工厂时归属，双厂同料不猜测、不串计。
- 库存风险按每个冻结仓库池检查阈值配置；本仓缺配置即产生该仓的 STOCK_POLICY_MISSING，无仓库池才保留无仓库号风险。既有阈值与发布门禁不放宽。
- 新增跨厂同料、历史 NULL 唯一/歧义归属、多仓局部缺策略、跨厂问题键及真实 MySQL 持久化回归。第一次全量运行发现新测试把实际生成 2 个问题误断言为 4，已更正；第二次全量运行 clean exit。

### 本轮验证
- `backend/./mvnw -q compile`：退出 0；最终 `backend/./mvnw -q test`：退出 0，Surefire 92 suites / 646 tests / 0 failure / 0 error / 0 skipped。专项真实 MySQL/引擎 24/24。
- `frontend/npm run build`：退出 0；`frontend/npm test -- --run`：103/103；5180 Playwright 双工作台 8/8。
- 8180 使用原隔离库环境变量重启，Flyway 061→062 成功；5180 排产页/缺料页 200，代理未登录批次/缺料/库存池接口 401，浏览器真实登录通过。未操作旧 #73 业务批次。
- 8180 `/actuator/health` 约 30 秒返回 503/DOWN；OracleHikariPool 取连接超时。仍缺专用 v5 全链路业务批次，故仅本轮 P1 代码回归完成，整体继续 REVIEW，等待独立复核。

## 真实 v5 双岗位发布闭环续验（2026-10-29）
- 新增 `TwoWorkbenchRealEngineFlowIntegrationTest`：使用独立 Testcontainers MySQL、两名不同用户及专属 `E2E-` 物料/库存/日历夹具，真实调用预检 API、计算任务 Worker、排产确认 API、物料分析任务 Worker、物控确认 API、异步发布 Worker。未改动隔离业务库中的旧批次。
- 验证同一 active run：整机净需求 8 台，物料拆分后末级净需求 15 件；物料分析前拒绝执行、计划员无权发起物料分析、未物控确认时拒绝发布；发布产生新批次和新 run，复制整机与物料数量及两名不同确认操作者，来源月守恒检查为零异常。
- 测试首次揭示发布 Worker 无 HTTP 用户上下文而调用带工厂访问检查的复核状态接口，导致发布任务 403/FAILED；已在 `MrpWorkbenchReviewService` 提取内部状态计算，后台发布校验使用该方法，HTTP 状态接口仍保留工厂权限检查。保留原失败证据，不放宽发布双确认门禁。
- 最终门禁：`backend/./mvnw -q -DskipTests compile` 退出 0；`backend/./mvnw -q -Dtest=TwoWorkbenchRealEngineFlowIntegrationTest test` 退出 0（1/1）；`backend/./mvnw -q test` 退出 0，Surefire XML 为 93 suites / 647 tests / 0 failure / 0 error / 0 skipped。前端 `npm run build` 退出 0、`npm test -- --run` 104/104；5180 上 Playwright 双工作台 8/8，重启 8180 后再次 8/8。
- 8180 用原隔离 `mrp_testkit` 环境变量加载新编译代码并启动，`/actuator/info` 与 `/actuator/health` 均为 200；5180 `/planning`、`/shortage` 均为 200，代理未登录批次接口为 JSON 401。实际登录后，当前发布批次、#73 复核、#73 缺料分页（2304 条，首 20 条）及排产接口均返回 200。此 #73 是既有 v4 历史批次，不能作为 v5 浏览器全链路证据。
- Oracle 同步源独立只读验证：`172.17.210.203:1521` TCP 可连，但按 `OracleDataSourceConfig` 的 JDBC 地址及 `BomService` 的上海组织计数 SQL 登录时出现 `ORA-17800` 协议读取错误，未取得 BOM 数量；`/actuator/health` 的 200 只覆盖本地主库，不能证明 NC/Oracle 可用。未发起 BOM 同步或更改连接配置。
- `git diff --check` 对本次业务服务无空白错误。Oracle 外部数据源恢复与专用 v5 浏览器写入演练尚未完成，双工作台整体任务仍为 REVIEW。
- 原独立 Reviewer 复核结论：本轮真实 v5 引擎流和后台确认修复**限定 PASS**；核对专项 1/1、后端全量 647/647、前端 104/104、8180 重启后 5180 浏览器 8/8。C025 追踪矩阵已同步区分已验证的 API/Worker 闭环与待验证的 v5 浏览器写入。Oracle `ORA-17800` 和专用 v5 浏览器写入演练仍开放，阶段 16/17/18/19 及双工作台整体状态继续 REVIEW，不标记 DONE。
