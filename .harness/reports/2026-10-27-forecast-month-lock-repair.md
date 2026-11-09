# 经营计划月份锁定与取消放弃修复

## 状态
REVIEW：实现方三层验证已通过，等待独立复核。

## 修复前证据与根因
- 编辑、新增弹窗允许增加或移除月份，服务端仅校验至少一个月份。
- Element Plus 取消弹窗以字符串 cancel 拒绝 Promise；原判断先转换成未知错误，导致误报。
- 上轮标题栏测试没有覆盖上述交互。

## 本次完成
- 编辑只能修改原有月份数量；新增自动填充当前版本月份，数量默认为 0。版本详情 API 返回完整月份集合。
- 后端拒绝编辑、恢复的月份增删，以及新增料号偏离当前版本月份集合的请求。
- 取消或关闭确认框不再显示错误，也不发送放弃请求。
- 补充后端集成与浏览器回归测试。

## 修改文件
- 前端 ForecastImport.vue、forecast-month-lock.spec.ts、forecast-version-heading.spec.ts。
- 后端 ForecastWorkspaceService.java、ForecastWorkspaceMapper.java/XML、ForecastController.java、ForecastWorkspaceIntegrationTest.java。

## 业务不变量
- 正式版本不可变；草稿行工厂料号唯一；编辑不改变月份结构，新增遵循当前版本月份结构。
- 未修改 Flyway 迁移或主库业务数据。

## 验证命令与结果
- backend: ./mvnw compile -q，成功。
- frontend: npm run build，成功。
- backend: ./mvnw test -q，87 套件、612 项、0 failure、0 error；ForecastWorkspaceIntegrationTest 4/4 通过。
- frontend: npm test，12 文件、100 项通过。
- Playwright: forecast-month-lock.spec.ts 与 forecast-version-heading.spec.ts，2/2 通过。
- 重启主后端 8080；经 5180 代理，认证当前版、草稿、版本详情 API 成功，版本详情返回 6 个完整月份；未经认证 API 返回 401。
- git diff --check，通过。

## 未完成与风险
- 未再次执行旧的写库发布全流程 E2E；本次浏览器取消测试拦截草稿请求以保护主库，增删月份 API 由真实 MySQL 集成测试验证。
- 任务开始前工作树已有大量其他未提交改动，未清理或覆盖。

## 下一步
- 等待独立复核，保持 REVIEW。

## 2026-10-27 补充浏览器验收
- 对照当前 `ForecastImport.vue`、工作台控制器、服务层和旧版全流程用例，发现此前的全流程浏览器脚本仍点击已经移除的行内版本差异按钮；直接重跑在该步骤 90 秒超时。已更新脚本，使其操作右上角统一入口和固定月份表单。
- 在隔离 MySQL `forecast_workspace_verify`、后端 8099、Vite 5191 上通过真实 API 及 Chromium 验证：登录、当前版、编辑、新增、删除、恢复、行日志、统一差异、发布预览、新版本发布、旧版不可变和草稿清空；另验证真实草稿下编辑/新增月份锁定、服务端拒绝增减月份、取消放弃不请求接口、确认放弃清理草稿。
- 补充历史版只读、返回最新版本、料号差异搜索、空结果、导入弹窗及模板下载；发现弹窗中 30,000/50,000 行文案冲突，统一为 50,000 行后构建和单测通过。
- 主环境 5180 经重启的 8080 后端执行只读版本标题/历史浏览器用例通过；主环境登录接口 200，未认证 Forecast API 401。
- 最终 `./mvnw compile -q` 和 `./mvnw test -q` 均退出 0；Surefire 87 套件、612 项、0 失败、0 错误、0 跳过。`npm run build`、`npm test`（100 项）、`git diff --check` 均通过。
- 继续补测隔离库真实 Excel 上传、staging 预览、确认导入、新版本与历史版本追溯；无效 Excel 错误提示、禁止确认及错误报告下载也通过。中间串行联跑暴露用例相互污染：导入替换了料号集合，失败用例留下草稿；已清理隔离测试草稿、调整用例先后顺序与断言，最终五项 Forecast Chromium 用例串行联跑 **5/5 通过**，约 21.8 秒。
- 本轮未执行排产重新生成的异步计算、全站所有页面或多浏览器矩阵；这些不能称为已完整验收。

## 2026-10-27 再次验收与跨页缺陷
- 承认此前“完整浏览器验收”的表述不准确：新增的导入后排产跨页检查曾失败。隔离库无上海组织已同步 BOM，`GET /mrp/batches/current-bom-status` 通过 `loadCurrentBom()` 抛异常；生成排产弹窗因此提前进入错误状态，经营计划未默认选中新版本。
- 修复准备数据接口：`BomSnapshotService.countCurrentBomEdges()` 对空 BOM 返回 0，但仍对无效 BOM 报错；`MrpBatchController` 使用该方法。真正创建排产时仍由 `loadCurrentBom()` 拒绝空 BOM。新增单测验证这两个边界。
- 重启隔离后端 8099 后，真实 API `current-bom-status` 返回 HTTP 200 / `localCount=0`，草稿接口返回 HTTP 200 / 空对象；浏览器生成排产弹窗默认展示刚导入的 V17，并提示缺少工厂仓库映射。未伪造 BOM 或创建排产批次。
- 在隔离库后端 8099、Vite 5191 上串行执行五项 Forecast Chromium 用例：5/5 通过（只读版本/差异/导入错误、有效 Excel 导入及跨页、月份锁定与取消、版本元信息、增删改恢复日志及发布）。主环境 5180 另跑只读标题/历史浏览器用例：1/1 通过。
- 后端 `./mvnw compile -q` 成功，`./mvnw test -q` 退出 0，Surefire 87 套件、613 项、0 失败、0 错误；前端 `npm run build` 成功，`npm test` 12 文件/100 项通过；`git diff --check` 通过。隔离库最后无活动草稿。
- 验收边界：未在有完整 BOM、库存、仓库映射、业务周和分类角色的环境执行真正的排产创建与异步重算，也未覆盖全站与多浏览器。当前结论仅为经营计划指定流程的隔离浏览器验收通过，不能称为整个 MRP 系统完整验收。保持 REVIEW，等待独立复核。
