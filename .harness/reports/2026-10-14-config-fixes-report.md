# 2026-10-14 配置筛选、导入日志与 API 回退修复报告

## 状态

REVIEW。实现与自测通过，独立阶段复核尚未执行；不修改既有阶段为 DONE。

## 输入与真实代码验收

已沿页面 → mrpApi/client → ConfigMasterController/Service → ConfigMasterMapper/AuditService → audit_log 查询追踪，读取项目入口、执行铁律、Repair/阶段17 Prompt、原型34–53行、导入和列表规范。

修复前真实浏览器复现：库存页清空仓库发送 warehouseCode=undefined，原 total=2，清空后 total=0。物料策略同样直接序列化清空值。
四类导入原先只写 resource_id=IMPORT 的文件汇总，而行内日志按具体业务键查；业务周按月记 CREATE/UPDATE，缺少导入动作和文件名。
未知且不含点的 API 路由会被 WebMvcConfig 的 /** 资源处理器回退为 index.html；当前服务的缺失策略接口在修复前已经可正常返回 JSON，未复现用户当时具体的请求现场。因此确认 HTML 回退机制，未推断当时必然是某一条 URL 拼错。

## 本次修改

- WebMvcConfig：API 未匹配时抛出404，由现有异常处理器返回 JSON；SPA 页面路由保留正常 HTML。
- client.ts：普通请求和上传遇到非 JSON 成功响应时给出明确 ApiError。
- mrp.ts：策略列表和缺失清单参数省略 null/undefined/空白；日志请求改为精确 resourceId，后端在分页之前精确筛选。
- 库存、物料、日历和缺失清单筛选：明确 value-on-clear为空字符串。
- ConfigMasterService：库存、物料、工厂仓库映射、生产日历按业务键写 IMPORT 日志，保留既有文件汇总；业务周按月份写 IMPORT。具体日志包含 before、after.data、after.fileName，和业务写操作处于同一事务。
- 五个配置页 showLogs：请求失败时显示错误提示。
- 增加前端非JSON/清空查询回归测试、后端五类日志及API/SPA回归测试。
- 同步 frontend/dist 至后端静态资源，重新启动8080主服务和8081独立验收服务。

库存页之前已实现的物料选择器仍使用真实物料表，按编码/名称/规格模糊搜索并分页。本次修复列表筛选与导入日志。

## 修改文件

- backend/src/main/java/com/mrp/config/WebMvcConfig.java
- backend/src/main/java/com/mrp/audit/controller/AuditLogController.java
- backend/src/main/java/com/mrp/mrp/service/ConfigMasterService.java
- frontend/src/api/client.ts、mrp.ts
- frontend/src/views/mrp/{StockPolicyConfig,MaterialPolicyConfig,FactoryWarehouseMappingConfig,ProductionCalendarConfig,BusinessWeekConfig}.vue
- frontend/src/views/mrp/components/MaterialPolicyNoticePanel.vue
- backend/src/test/java/com/mrp/config/WebMvcConfigTest.java
- backend/src/test/java/com/mrp/mrp/service/ConfigMasterServiceTest.java
- frontend/src/api/__tests__/{client.test,mrp-query.test}.ts
- backend/src/main/resources/static 构建产物

## 业务不变量与依赖

策略未维护时继续下钻、有效STOP生效、缺失清单随排产结果保存。未修改计算规则和数据库迁移，未修改工具链/框架版本，未新增应用依赖。验证导入使用独立 mrp_policy_acceptance_v2 数据库，主库仅做只读查询。

## 三层验证

```text
cd backend && ./mvnw compile
BUILD SUCCESS
cd frontend && npm run build
通过 TypeScript 与 Vite 构建；保留既有大chunk警告。
cd backend && ./mvnw test
257 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS
cd frontend && npm test
5 files / 33 tests passed
python3 test-evidence/config-fixes-2026-10-14/verify-api.py
真实Excel下载模板、预览、首次导入及再次更新，五类日志包含准确资源键、原值和文件名。
预览及非法文件没有写入日志；精确查询不混入其他业务键。
node test-evidence/config-fixes-2026-10-14/verify-browser.cjs
Chrome真实页面：仓库/供应/下钻选择→清空恢复总数，五类查看日志显示导入和文件名，缺失清单清空恢复3条，0 pageerror，无undefined/null查询。
curl http://localhost:8080/mrp/stock-policies
200 text/html
主服务已登录只读查询：batch90缺失清单200 JSON(total0)，库存策略total2、物料策略total9。
独立验收服务：未知API404 JSON，已完成批次缺失策略200 JSON(total3)。
```

证据：test-evidence/config-fixes-2026-10-14/ 下包含 API/浏览器日志、JSON断言结果、五类日志弹窗截图、缺失清单截图、验证脚本与编译测试摘要。完整构建/测试日志位于 /tmp/mrp-config-fix-{compile,build,tests,front-tests}.log。

## 未完成与风险

- 历史导入只保存汇总，无法可靠重建旧逐条修改内容；本修复覆盖后续导入。
- 历史计算没有冻结缺失清单时，接口可能返回空集合；本次未篡改历史计算结果。
- 尚无独立Reviewer PASS，保持 REVIEW。

## 决策与下一步

无新增业务决定。用户可刷新页面使用本次修改；阶段独立复核按既有流程执行。
