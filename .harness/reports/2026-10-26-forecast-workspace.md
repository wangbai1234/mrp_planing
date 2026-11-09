# Forecast 工作台改版任务报告

## 状态

REVIEW（实现和自测完成，等待独立阶段复核）

## 本次完成

- Forecast 页改为当前计划、编辑草稿、版本差异、操作日志四个工作区；历史版本保留只读入口，不在主列表堆叠历史料号。
- 每次人工调整先写唯一草稿；支持创建草稿、料号新增、编辑、逻辑删除、放弃草稿和发布。草稿记录创建人/时间、更新人/时间及修订号；新增、编辑、删除、发布须填写原因。
- 发布生成完整不可变正式版本，并更新当前版本指针；导入确认与人工草稿互斥。历史版本及已经绑定版本的排产计算保留追溯，受影响的未发布批次转为 STALE。
- 支持当前版与草稿、两个正式版本之间按工厂＋料号＋月份比对，识别新增、删除、数量、优先级、属性变化；提供差异摘要、料号历史和操作日志。
- 增加创建、编辑、删除、发布、日志权限及工厂数据域校验。排产新建默认使用当前 Forecast 版本。

## 修改文件

- 数据库：`backend/src/main/resources/db/migration/V055__forecast_workspace.sql`。
- 后端：`backend/src/main/java/com/mrp/forecast/{controller/ForecastWorkspaceController.java,service/ForecastWorkspaceService.java,repository/ForecastWorkspaceMapper.java}`、`backend/src/main/resources/mapper/forecast/ForecastWorkspaceMapper.xml`，以及 Forecast 导入、版本查询、审计、权限相关文件。
- 前端：`frontend/src/views/forecast/ForecastImport.vue`、`frontend/src/views/mrp/MrpWorkspace.vue`。
- 测试：`backend/src/test/java/com/mrp/forecast/ForecastWorkspaceIntegrationTest.java`、权限回归测试。

## 业务不变量

- 同时只有一份活动草稿；发布后创建新完整版本，历史版本的 `forecast_detail` 不回写。
- 草稿行按工厂＋料号唯一；删除只影响草稿，发布后才影响当前正式版本。
- 写操作使用修订号阻止旧页面覆盖新数据；版本指针与发布事务一致。
- 手工调整和发布写入操作者、前后值及原因审计；跨工厂读取和修改受权限约束。
- 新版本发布或导入不会悄悄改写排产批次已经绑定的历史 Forecast 版本。

## 验证命令与结果

```text
cd backend && ./mvnw compile -q
通过。

cd backend && ./mvnw test -q
通过；Surefire XML 87 个套件、610 项测试、0 failure、0 error、0 skipped。

cd frontend && npm run build
通过。

cd frontend && npm test
通过；12 个测试文件、100 项测试。

git diff --check
通过。
```

- 独立 MySQL schema `forecast_workspace_verify` 上应用并校验 Flyway V055；此前完成真实 API 草稿创建、新增、编辑、差异摘要、发布、旧修订冲突和审计联调。隔离库保留两版测试数据，临时草稿已放弃，未写入主库。
- 最终代码重启隔离后端 `localhost:8099` 后，认证请求 `GET /forecast-workspace/current`、`/draft`、`/compare?oldId=1&newId=2`、`/audit-logs?resourceType=forecast` 均返回 HTTP 200；当前版为 V2，差异返回 1 行数量变化，日志返回 8 条。未认证请求返回 401。
- Vite `localhost:5191/forecast` 返回 HTTP 200；此前浏览器已检查当前版、空草稿、版本差异和操作日志，页面无报错。

## 未完成与风险

- 尚未进行独立阶段复核，因此保持 REVIEW，不标记 DONE。
- 工作区在本任务开始前已有大量未提交改动，本任务未清理或重置它们；提交时应按本任务文件范围单独复核。

## 决策/待确认

- 采用“单一编辑草稿 → 发布完整正式版本”，主列表仅显示所选正式版本；历史记录通过差异、料号历史和操作日志按需查询。

## 下一步

- 独立复核发布事务、权限与数据域、版本差异边界及页面操作；复核 PASS 后再将任务置为 DONE。

## 2026-10-28 制单人、业务状态与新增料号查询

- 页面不再提供业务制单人的录入或展示；已应用的 V055 迁移仍保留兼容字段，业务流程不读取该字段。
- 表格将 Excel 原有 `status` 字段明确标为“业务状态”，表头说明仅用于查看和版本属性差异；该字段不是版本或发布状态，不参与排产计算。
- 新增弹窗复用库存策略 `PolicyMaterialPicker`，经 `GET /api/v1/forecast-workspace/materials/page` 分页模糊搜索有效物料编码、名称、项目型号和规格；编辑已有料号时锁定选料。
- 后端 `./mvnw compile -q` 通过；`ImportConfirmationIntegrationTest` 单独运行通过；前端 `npm run build` 及 `npm test` 通过（12 文件、100 项）；隔离环境 Chromium 经营计划完整闭环 1/1 通过，包含新增料号的模糊查询和选中，以及编辑、删除、恢复、差异与发布。
- 一次并行运行的后端全量测试出现 21 项 `ImportConfirmationIntegrationTest` 上下文错误，根报错为缺失 `StockPolicyAccessMapper` Bean；单独重跑该类通过。停止重叠运行后，另一次完整 `./mvnw test` 通过：618 项、0 失败、0 错误、0 跳过，`BUILD SUCCESS`。未做独立 Reviewer 复核，继续保持 REVIEW。

## 2026-10-26 主页面联调补验

- 用户反馈 `localhost:5180/forecast` 的当前计划、草稿均提示接口不存在。核对实际链路发现：5180 Vite 代理到 8080；8080 进程自 13:45 启动，仍加载旧版路由；先前只重启过连接隔离库的 8099，因此未覆盖用户使用的主页面。
- 重启 8080 的 Spring Boot `local` 进程；启动日志确认 `mrp` schema 已处于 Flyway V055，应用正常启动。
- 带认证请求经 5180 代理访问 `/api/v1/forecast-workspace/current` 与 `/draft` 均返回 HTTP 200；当前正式版本为 V3，草稿为空。Chrome 刷新 `localhost:5180/forecast` 后显示 V3 料号和月份数据，未见页面错误日志。

## 2026-10-26 简化主表交互续修

### 状态

REVIEW（实现和自测完成，等待独立复核）

### 本次完成

- 移除独立的草稿、差异和日志页签。主表提供编辑、删除、查看日志、版本差异；顶部保留新增、导入、查看版本、全量差异、生成新版本与放弃变更。
- 仅当前生效版本可编辑或删除；保存时透明创建/复用草稿，待发布调整直接显示在主表。正式历史版本只读，差异与日志通过弹窗分页查询。
- 发布前展示服务端差异摘要和明细、填写原因，并提示相关未发布排产批次失效且需重新生成；前后端均拒绝无差异新版本。已删除料号可从差异弹窗选择恢复。
- 为行编辑增加工厂＋料号精确草稿行查询；为行内差异增加精确工厂筛选；草稿数量合计在后端使用 BigDecimal 计算，草稿查询拷贝 MyBatis 行 Map 后解码，避免污染同请求一级缓存。
- 草稿创建权限允许有编辑或删除权限的用户触发操作；既有正式版本、排产绑定与审计逻辑不变。

### 修改文件

- `frontend/src/views/forecast/ForecastImport.vue`
- `backend/src/main/java/com/mrp/forecast/{controller/ForecastWorkspaceController.java,service/ForecastWorkspaceService.java,repository/ForecastWorkspaceMapper.java}`
- `backend/src/main/resources/mapper/forecast/ForecastWorkspaceMapper.xml`、`backend/src/main/java/com/mrp/config/SecurityConfig.java`
- `backend/src/test/java/com/mrp/forecast/ForecastWorkspaceIntegrationTest.java`

### 验证结果

- `cd frontend && npm run build && npm test`：通过，12 个文件、100 项测试。
- `cd backend && ./mvnw compile`：通过；`./mvnw -q -Dtest=ForecastWorkspaceIntegrationTest test`：2 项通过，覆盖精确行读取、无变化发布拒绝、删除与恢复。
- 主后端已重启在 8080。经 5180 代理认证查询当前版、草稿、版本明细、工厂过滤差异均为 HTTP 200；不存在的待删除行返回预期 404。
- Chrome 实际访问 `localhost:5180/forecast`：主表单行显示四项操作；版本差异和版本记录弹窗可打开；无页面异常、无失败 API。
- `git diff --check`：通过。

### 未完成与风险

- 尚未做独立 Reviewer 复核，保持 REVIEW。
- 主库现有数据未创建测试草稿，也未触发实际版本发布；人工修改发布流程由隔离库集成测试覆盖。
- 本次仅重启进程，没有写入主库业务数据，也未修改项目技术栈配置。

### 续修复核

- 新增精确草稿行读取测试时发现 MyBatis 同请求一级缓存中的 Map 会被月份解码原地修改，第二次读取丢失原始 JSON；现对查询结果先拷贝再解码，定向集成测试 2/2 通过。
- Chrome 经 5180 打开当前版的编辑弹窗，显示工厂、料号及月份；行内四项操作、版本差异及版本记录弹窗均可见。主后端最终于 17:31 重启成功，8080 监听，新 API 通过 5180 代理返回 200。
- 后端完整 `./mvnw test` 回归于 17:35 结束：610 项、0 失败、0 错误、BUILD SUCCESS；前端构建及 100 项测试通过。

## 2026-10-26 隔离库完整闭环补测

### 状态

REVIEW（完整业务闭环和负向用例已由实现方自测，独立 Reviewer 尚未复核）。

### 环境与执行

- 隔离 MySQL `forecast_workspace_verify`，后端 `localhost:8099`，前端 `localhost:5191`；确认前端代理指向隔离后端。主环境 `5180/8080` 只做认证读取，未写业务数据。
- 后端隔离进程原先仍加载旧代码，首次浏览器测试在精确草稿行接口遇到 404；按当前代码重启 8099 后接口返回 200。测试途中一度用错隔离 MySQL 密码导致启动拒绝，改为读取测试容器配置后启动成功，未改项目配置。
- `cd backend && ./mvnw compile`：BUILD SUCCESS；`./mvnw test`：610 项、0 失败、0 错误，BUILD SUCCESS，记录 `/tmp/forecast-e2e-backend-test.log`。
- `cd frontend && npm run build`：通过；`npm test`：12 文件、100 项通过；`npx playwright test e2e/forecast-workspace.spec.ts --project=chromium --reporter=line`：1 项通过，约 9.7 秒。可重复用例位于 `frontend/e2e/forecast-workspace.spec.ts`。

### 实际验证

- 浏览器经真实 API 完成登录、打开当前版本、行内日志与版本差异、编辑现有料号、新增新料号、删除现有料号、差异弹窗恢复、再次查看操作日志、发布预览与风险提示、生成新版本。测试采用隔离库创建的独立料号。
- 发布后当前版本从 V3 变为 V4；差异恰为 `QUANTITY` 与 `ADDED` 两行，旧版数值未改，活动草稿为空；页面没有 5xx API 或运行时异常。之前同一用例已实际生成 V3，确认主表发布后刷新显示新版本。
- 负向联调：无变更发布返回 400、旧修订号修改返回 409、无认证读取返回 401；只读角色读取当前版返回 200，创建草稿与查看无授权日志返回 403；授予单独创建权限后可创建草稿，但编辑、删除、发布均返回 403。负向测试草稿已放弃，版本指针不变。
- 5180 主环境当前版、草稿、版本列表只读 API 均为 200；当前仍是 V3、没有活动草稿。

### 剩余边界

- 追加隔离库联调：在 V4 下创建临时 `READY` 与 `PUBLISHED` 批次各 1 个，修改并发布 V5 后，前者变 `STALE` 且版本字段加 1，后者维持 `PUBLISHED` 且版本字段不变；临时批次已清理。尚未验证业务重新生成排产的完整异步计算结果。
- 本次没有重新执行 Excel 导入确认和多浏览器兼容矩阵；它们不属于该人工编辑发布闭环。独立 Reviewer 仍未复核，因此状态保持 REVIEW。

### 环境备注

- 隔离后端的 Oracle 连接池尝试连接未配置的外部服务，使 `/actuator/health` 在最终检查时超时；Forecast API 本身仍响应，前后端测试闭环在此前已通过。未更改 Oracle 或项目技术栈配置。
