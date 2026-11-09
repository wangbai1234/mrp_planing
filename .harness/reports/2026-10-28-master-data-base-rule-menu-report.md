# 基础信息与基础规则菜单调整报告

## 状态
DONE（工程与测试门禁）：实现、三层验证及浏览器联调通过，独立 Reviewer 于 2026-10-28 给出 PASS；产品经理仍需核验菜单业务口径。

## 本次完成
- 基础信息按工厂管理、仓库管理、物料管理、物料分类、BOM管理展示；基础规则按物料策略、库存控制策略、生产日历、业务周期间展示。
- 工厂与仓库拆为独立页面、独立查看/维护权限；原计算配置入口保留为基础规则导航，原映射入口跳转仓库管理。
- 工厂及仓库列表增加服务端分页、关键词搜索、状态/品质筛选，保留新增、编辑、删除、日志和仓库导入。
- 新建 V057 权限迁移，按既有仓库授权回填工厂权限；缓存键换版，避免迁移后继续读取旧授权。
- 审计日志按资源类型与资源 ID 授权，继续执行工厂数据范围过滤。

## 修改文件
- 前端：`frontend/src/config/menu.ts`、`pageLabels.ts`、`frontend/src/router/index.ts`、`frontend/src/views/mrp/FactoryMasterConfig.vue`、`FactoryWarehouseMappingConfig.vue`、`MrpConfigurationCenter.vue`、`frontend/src/api/mrp.ts` 及菜单测试。
- 后端：`V057__split_factory_warehouse_permissions.sql`、`SecurityConfig.java`、`AuditLogController.java`、`PermissionCacheService.java`、工厂/仓库 Controller、Service、Mapper 及范围/权限测试。

## 业务不变量
- 已应用的 V056 不改写；一厂多仓、良品属性和仓库优先级逻辑不变。
- 旧 API 保持兼容；列表新增 `/page` 路径供维护页面使用。
- 仅可查看授权工厂的主数据与日志；工厂、仓库功能权限互相独立。

## 验证命令与结果
```text
cd frontend && npm run build && npm test                   通过，101/101
cd backend && ./mvnw compile && ./mvnw test                通过，620/620（最终改动之后，2026-10-28 14:31）
cd backend && ./mvnw -Dtest=ConfigFactoryScopeIntegrationTest,AuditLogFactoryScopeIntegrationTest,EndpointPermissionRegressionTest test
                                                            通过，31/31（分页改动之后）
本地服务 18080：V057 升级通过；工厂/仓库分页与筛选 API 200；新增、修改、日志、删除闭环全部 200。
Chromium 浏览器：工厂/仓库列表、分页、空搜索、日志、新增弹窗，四个规则页及旧入口均正常。
git diff --check                                            通过
```

## 未完成与风险
- 独立 Reviewer PASS；最终后端全量 625/625、前端 101/101、Chromium 4/4 通过，详见本次需求变更测试报告。
- 原有其他未提交改动未清理或重置。

## 决策/待确认
- 菜单顺序采用用户最新确认；工厂与仓库分开维护。

## 下一步
- 产品经理按需求核验稿确认菜单与页面归属。
