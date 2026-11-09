# 整机排产与物料缺料拆分：物料重算续修报告

## 状态
REVIEW。产品方案已获业务认可，代码和自测已完成；独立阶段复核及隔离业务批次完整写入闭环待执行。

## 本次完成
- /planning 只呈现整机排产，/shortage 呈现末级净需求、分配、风险、决策和来源整机；两页复用 batch/active run，并通过整机及末级料号互跳。
- 物控更改库存分配后，批次进入 MATERIAL_RECALC_REQUIRED，旧物料复核不可发布；重算在同一事务内替换旧物料事实，失败保留旧事实。旧人工决策未定义迁移口径时明确阻断。
- 整机与后代同码时按路径层级区分分配，物料可用库存扣除整机占用；已确认整机净需求仍按经营计划优先级处理。
- v5 库存风险按仓库库存池的实际已分配量评估，避免多仓各自重复扣工厂总用量；历史规则保留原算法。
- 新增真实 MySQL 成功路径测试：同一 run 删除旧物料逐层需求、末级需求和日备料并写入新事实，整机需求、根层需求和整机日事实的 ID 保持不变；与失败回滚测试共同覆盖事务的两个方向。
- 新增 MaterialAllocationWorkflowIntegrationTest，使用独立 Testcontainers MySQL 和真实分配、复核、批次、任务 Mapper，串起“已确认整机与物料→物控人工调整分配→物料确认失效且发布门禁拒绝→提交物料任务→Worker 消费并替换同一 run 的物料事实→物控重新确认→双确认门禁通过”。计算引擎和日分配的结果由测试夹具写入，不将其误报为全真实计算。

## 修改文件
- MaterialAnalysisStageService.java、MrpCalculationService.java、MrpTaskWorkerService.java、StockRiskService.java 及相关 Mapper/XML。
- MaterialAnalysisStageServiceTest.java、MaterialAnalysisRecalculationIntegrationTest.java、StockRiskServiceTest.java、StockRiskChunkIntegrationTest.java。
- MaterialAllocationWorkflowIntegrationTest.java。
- 产品设计方案、.harness/workflow.yaml、.harness/issue-log.md。

## 业务不变量
- 同一批次仅一个 active run；整机日事实和整机需求在物料重算中保持不变。
- 已处理决策不静默丢失；事务失败时旧末级需求、逐层需求及库存风险恢复。
- 同一工厂同料号的用量按具体仓库已分配库存计入风险，不重复扣量；安全库存仍由库存池 reserved 量硬保底。

## 验证命令与结果
- ./mvnw compile -q：PASS。
- ./mvnw -q -Dtest=ImportConfirmationIntegrationTest test：PASS；此前并发构建出现的 Bean 错误串行复跑未复现。
- ./mvnw test -q：最终全量回归 634/634 PASS，0 失败、0 错误；先前一次旧全量用例也串行通过。
- ./mvnw -q -Dtest=MaterialAnalysisRecalculationIntegrationTest,StockRiskServiceTest,MaterialAnalysisStageServiceTest,StockRiskChunkIntegrationTest test：PASS。
- 本轮 ./mvnw compile -q：PASS；./mvnw -q -Dtest=MaterialAnalysisRecalculationIntegrationTest test：2/2 PASS；串行 ./mvnw test -q：635/635 PASS，0 失败、0 错误、0 跳过。
- 续轮 ./mvnw compile -q：PASS；./mvnw -q -Dtest=MaterialAllocationWorkflowIntegrationTest test：1/1 PASS；随后串行 ./mvnw test -q：退出码 0，Surefire XML 共 90 套、636 项，0 失败、0 错误、0 跳过。git diff --check：PASS。
- 独立复核 FAIL 后修复租约：./mvnw compile -q PASS；./mvnw test -q 退出码 0，Surefire XML 共 91 套、638 项，0 失败、0 错误、0 跳过；MaterialTaskLeaseRecoveryIntegrationTest 2/2 PASS，使用独立提交的真实 MySQL 事务模拟三次崩溃租约、旧 Worker 无法续租、最终复位同一 active run。
- npm run build；npm test -- --run：PASS；前端 102/102。
- 隔离服务 8180 启动，Flyway v060；未登录 API 401；隔离前端 5181 登录后查看两页，物料汇总 134 行、受影响整机弹窗与整机页跳转 PASS。
- 本轮发现既有 5181 Vite 代理实际指向 8081，和 8180 隔离后端不是同一数据源。新启 5182 前端并显式代理至 8180；真实登录、当前批次 #62、排产页→缺料页、未生成时空数据和 API `reviews`/`shortage/page` 均验证通过。上一条 5181/134 行证据仅证明 8081 对应页面，不再作为隔离 8180 的数据验收证据。
- 2026-10-29 用户纠正正式前端端口为 5180。核对 `frontend/vite.config.ts` 与 `frontend/playwright.config.ts` 均固定 5180；此前 5180 进程沿默认代理连接未监听的 8080，使业务 API 返回 502。按 `MRP_VITE_API_TARGET=http://localhost:8180 npm run dev -- --host 0.0.0.0 --strictPort` 重启 5180 后，`/planning` 和 `/shortage` 均返回 200，`/api/v1/mrp/batches/current` 未登录返回后端 JSON 401（不再是代理 502）。在 5180 浏览器实际登录后，排产页读取隔离批次 #62/DRAFT，切换到 `/shortage/62` 显示同批次、无 active run、空末级需求和分页空态。5182 仅是先前临时隔离入口，不能代替项目规定的 5180 验收。
- 更新旧单页 Playwright 用例为双工作台断言：`npm run build` PASS、`npm test -- --run` 102/102 PASS、`npx playwright test e2e/phase2-mrp.spec.ts --project=chromium --workers=1 --reporter=line` 在 5180 实际浏览器 7/7 PASS。登录后的 5180→8180 API 核验：当前批次 #62/DRAFT、复核 `NOT_GENERATED/NOT_ANALYZED`、缺料分页 0 条、日计划 0 条，均与页面一致。

## 未完成与风险
- 尚未在隔离业务批次中完整执行人工分配调整→结果失效→重算成功→双岗复核→发布。#121/run #73 属于 8081 服务，有既存 49 项异常；没有擅自处理或发布。
- 本轮明确了隔离 8180 实际当前批次为 #62/DRAFT；#121/run #73 属于 5181→8081 另一套服务。真实 MySQL 成功测试用 mock 计算及日分配写入验证事务/同 run 行为，不能冒充 Worker/API/UI 全闭环。
- 重算对旧人工决策采用阻断策略，后续如需自动迁移须由业务明确规则；库存风险仓库口径已加单元测试，仍需真实多仓业务用例复核。
- 阶段 16/17/18/19 仍保持 REVIEW，不把本次专项自测等同整体阶段门禁通过。
- 续轮独立测试覆盖真实服务/SQL 的交接及 Worker 状态机；因计算引擎和日分配用夹具替身，尚不能替代完整真实 v5 计算与发布 API 联调。隔离 8180 的 /actuator/health 本轮返回 503，未登录 /api/v1/mrp/batches/current 返回预期 401；需单独查明健康检查 DOWN 依赖，不以 401 或前端静态页 200 冒充完整服务健康。
- 隔离 8180 重启为当前代码后，`/actuator/info` 返回 200、未登录批次 API 返回 401、5182 两页面返回 200。`/actuator/health` 仍约 30 秒返回 DOWN/503；线程栈及运行日志定位到独立 Oracle 数据源的健康检查在连接池借用等待，Oracle 连接线程卡在协议握手；MySQL 3317 和 Redis 6391 连通。本次未改 Oracle 数据源配置，健康门禁仍未通过。
- 5180 入口和代理错配已修复为运行参数；Oracle 健康探针问题与前端端口无关，仍需独立验证，不能用页面 200 代替完整健康门禁。
- 重新请求 8180 `/actuator/health`，约 30 秒后仍为 503/DOWN；这是 Oracle 数据源健康检查的独立问题。隔离当前批次 #62 是旧 `v4-rolling-business-weeks` DRAFT，创建新 v5 批次会触发 `ACTIVE_BATCH_EXISTS`；不为验收擅自作废/发布该批次，完整 v5 写入闭环仍待专用隔离数据环境。

## 决策/待确认
- 旧人工决策如何迁移尚未授权；当前明确拒绝重算，要求先确认迁移口径。

## 下一步
- 独立 Reviewer 首轮 FAIL：过期 RUNNING 任务在未达最大次数时无法重排，最终 FAILED 后物料批次仍停留 CALCULATING。已新增受版本和租约约束的受控重排，以及终态时同 run 物料批次复位；真实 MySQL 独立事务用例 2/2、全量回归 638/638 通过，隔离服务已重启，等待独立复核。
- 在专用测试批次完成真实 v5 引擎、日分配、双岗确认和发布 API/浏览器验收；查明健康检查 503。
