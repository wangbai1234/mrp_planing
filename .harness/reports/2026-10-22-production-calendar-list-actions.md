# 生产日历列表操作入口调整

## 状态
REVIEW

## 本次完成
- 按用户本次要求，将列表操作列统一为“查看日志 / 编辑 / 删除”，操作列固定在右侧。
- “查看日志”独立弹窗展示所选工厂、日期、操作人、动作、时间和变更内容，含加载、空数据、错误和重试状态。
- 列表编辑侧栏只展示日期设置表单；月历原有日志和删除入口保留。
- 删除以当前列表行的 ID 为准，确认框展示工厂和日期；未配置日期不展示删除；只读用户可查看日志、不能删除。

## 修改文件
- frontend/src/views/mrp/ProductionCalendarConfig.vue
- frontend/src/views/mrp/__tests__/ProductionCalendarConfig.test.ts
- .harness/workflow.yaml
- 本报告。

## 依据与真实链路
- 用户本次要求优先于原型原来的单一编辑入口；原型：prototype/MRP日排产Phase2可交互原型.html:48–53。
- 参照 FactoryWarehouseMappingConfig.vue、MaterialPolicyConfig.vue 和 StockPolicyConfig.vue 的列表操作列及日志弹窗。
- 日历分页：getCalendarPage → ConfigMasterController.calendarPage → ConfigMasterService.productionCalendarPage。
- 日志：getConfigLogs → /api/v1/audit-logs，以 mrp_production_calendar 和 工厂|日期 查询。
- 删除：deleteProductionCalendar → ConfigMasterController → ConfigMasterService.deleteProductionCalendar → ConfigMasterMapper.deleteProductionCalendar，按 ID 删除并写入原有审计日志。

## 业务不变量
- 不改日历数据结构、计算规则、权限规则、后端接口与迁移。
- 页面验收只查看记录、打开编辑和删除确认后取消，未保存或删除业务日历。

## 验证命令与结果
- frontend: npm run build，通过；存在既有 bundle 大小提醒。
- frontend: npm run test，12 个文件、92 项测试全部通过。
- backend: ./mvnw compile，通过。
- backend: ./mvnw test，585 项测试全部通过（Failures: 0, Errors: 0, Skipped: 0），BUILD SUCCESS。
- 本地前端以 127.0.0.1:5180 启动，后端以 local profile 在 8080 启动，均加载当前代码。
- GET /api/v1/mrp/production-calendars/page：200，总数31，当前页20条；爱培科2026-10-02产能10，取消编辑/删除后读回一致。
- GET /api/v1/audit-logs：200，爱培科2026-10-02共5条日志，页面弹窗显示对应记录。
- 浏览器验证：列表三个入口正常；未配置日期无删除；编辑侧栏无修改记录和删除；删除确认展示正确工厂日期，取消后记录保留。
- 测试覆盖：独立日志查询及目标、编辑不再查询日志、删除取消、确认删除使用所选行 ID 并刷新列表、只读权限和日志空状态。
- 运行日志：/tmp/mrp-calendar-actions-{build,frontend-tests,backend-compile,backend-tests,frontend-runtime,backend-runtime}.log。
- 页面证据：test-evidence/2026-10-22/calendar-list-actions.png、calendar-logs-dialog.png、calendar-list-edit-drawer.png。

## 未完成与风险
- 等待独立阶段复核，当前状态 REVIEW。
- 真实业务数据未执行删除提交；提交调用及刷新行为由组件测试验证。

## 决策/待确认
- 无业务决策待确认。

## 下一步
- 等待独立阶段复核。
