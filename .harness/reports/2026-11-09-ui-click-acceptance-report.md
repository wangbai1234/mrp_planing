# 页面统一与浏览器点击验收报告

## 状态

REVIEW。当前需求的页面实现与本轮可复现回归已通过；阶段 18/19 的专用夹具全量门禁未在本轮全部通过，不据此宣称所有业务场景已验收。

## 本次完成

- `0043f28` 已将页面统一、生产执行搜索和服务端分页的源码与后端迁移推送至 `origin/main`。本轮补充页面点击巡检、可移植的 Playwright 地址配置和经营计划闭环用例。
- 管理员逐一点击 15 个主菜单入口及 3 个管理子页，检查页面加载、可见搜索框回车、可用分页翻页与右对齐、常见弹窗和 API 5xx。
- 生产执行页验证部分料号回车模糊搜索、清空恢复、服务端页大小切换和右下角分页；后端托管静态包也执行了相同验证。
- 经营计划完成编辑、新增、删除、恢复、日志、差异和发布的浏览器闭环。修复审计日志的 `ADD`/`RESTORE` 中文动作，以及切换料号时短暂显示前一料号日志的问题。
- 在隔离 MySQL/Redis/API/Worker 环境验收导入：10 类错误工作簿均显示错误预览、禁用确认并可下载错误报告；经营计划和库存合法文件均从页面预览确认生成正式版本。

## 修改文件

- `frontend/playwright.config.ts`、`frontend/e2e/all-pages-click-audit.spec.ts`、`frontend/e2e/forecast-acceptance.spec.ts`、`frontend/e2e/forecast-workspace.spec.ts`
- `frontend/src/utils/auditPresentation.ts`、`frontend/src/utils/__tests__/auditPresentation.test.ts`、`frontend/src/views/forecast/ForecastImport.vue`
- `backend/src/main/resources/static/index.html` 与本次构建所引用的静态资源

## 业务不变量

- 生产执行搜索与分页由服务端处理；搜索回车或清空后回到第一页，日期筛选和已发布版本选择保持原语义。
- 导入错误文件不得确认；合法文件须经预览与用户确认后生成版本。
- 审计日志按工厂和料号筛选，切换对象时不得显示上一对象的日志。
- 验收写操作只作用于独立复制的测试数据库，未改动原验收库。

## 验证命令与结果

```text
frontend: npm run build                         PASS
frontend: npm test -- --run                     PASS (118 tests)
backend:  ./mvnw compile                       PASS
backend:  ./mvnw test                          PASS (731 tests, 0 failures/errors/skips; 13:28)
Playwright (built backend static, :8182): navigation/login/forecast/execution/etc. 19 PASS
Playwright (isolated frontend, :5192): import boundary + positive flow 11 PASS
Playwright (isolated frontend, :5192): forecast full CRUD/publish 1 PASS
Playwright (isolated frontend, :5192): forecast version heading 1 PASS
curl :8182/actuator/health, /, new JS asset    HTTP 200
```

本轮有效浏览器用例共 32 个；其中菜单巡检用例实际访问 18 个页面入口。页面巡检的弹窗操作覆盖常见命令，不等于每个页面的全部写入、删除和异常分支均已覆盖。

## 未完成与风险

- `current-planning.config.ts` 的 30 项专用业务套件依赖另一套冻结输入。此前在当前隔离库副本运行时，前两项通过，后续因 `INVALID_INVENTORY_SNAPSHOT` 和前置数据不匹配停止；不能记为全套通过。
- 专用角色权限、Worker 失败恢复、所有生产计划发布和执行确认的组合场景未在本轮逐项点击，需使用相应专用夹具执行。
- 测试截图、Playwright 报告和大体积测试转储保留在本地，未纳入源码提交。

## 决策/待确认

- 无业务规则变更。由于阶段独立复核和专用夹具门禁尚未完成，维持原 `workflow.yaml` 的 REVIEW 状态。

## 下一步

- 在各套件对应的隔离夹具上执行剩余阶段 18/19 浏览器用例，逐项记录通过、数据前置失败和产品缺陷。
