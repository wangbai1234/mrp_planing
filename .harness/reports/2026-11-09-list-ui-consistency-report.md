# 列表页面交互与布局统一报告

## 状态

REVIEW（实现和自测完成，待独立复核）

## 本次完成

- 删除应用顶部所有页面共用的“数据已同步”提示。
- 统一分页靠右、筛选区搜索框靠右和页面头部操作区靠右；操作日志页的日期选择器不参与搜索框排序。
- 页面列表搜索统一为回车提交、清空立即重查；移除重复的“搜索/查询”按钮。角色、物料分类与权限配置原有本地模糊筛选也改为回车提交。
- 导入、导出、新增使用 Element Plus 主按钮样式，并统一导入排在新增之前；保留版本对比等业务动作按钮。
- 更新已有浏览器用例，匹配当前版本标签和分岗菜单，并验证回车搜索、分页位置和同步提示移除。

## 修改文件

- `frontend/src/App.vue`、`frontend/src/main.ts`、`frontend/src/styles/list-layout.css`
- `frontend/src/views/admin/{AuditLog,PermissionConfig,RoleList,UserList}.vue`
- `frontend/src/views/material/{MaterialCategoryView,MaterialView}.vue`
- `frontend/src/views/{bom/BomView,export/ExportConfig,forecast/ForecastImport,inventory/InventoryImport}.vue`
- `frontend/src/views/mrp/{BusinessWeekConfig,FactoryMasterConfig,FactoryWarehouseMappingConfig,MaterialPolicyConfig,ProductionCalendarConfig,ShortageWorkspace,StockPolicyConfig}.vue`
- `frontend/src/views/mrp/components/{DecisionCenter,MaterialPolicyNoticePanel,PendingUnallocatedPanel,RollingPlanningGrid}.vue`
- `frontend/e2e/{latest-data-pages,factory-warehouse-menu}.spec.ts`

## 业务不变量

- 搜索仍调用现有分页 API，查询时重置到第一页；服务端已有的 `LIKE '%keyword%'` 模糊匹配不变。
- 筛选条件变化仍按原页面逻辑触发查询；导入、导出、排产及审批流程不变。
- 页面组件继续使用 Vue 3 和 Element Plus，不新增运行依赖或数据库迁移。

## 验证命令与结果

```text
frontend/npm run build                                                PASS
frontend/npm test                                                     PASS，118 tests
backend/./mvnw compile                                               PASS
backend/./mvnw test                                                  PASS，728 tests
frontend/npx playwright test e2e/latest-data-pages.spec.ts e2e/factory-warehouse-menu.spec.ts e2e/two-workbench-compact.spec.ts --project=chromium --reporter=line
                                                                      PASS，9 tests
真实服务 POST /api/v1/auth/login                                      200
真实服务 GET /api/v1/materials?page=1&pageSize=20&keyword=O-VX       200，命中料号中间片段
浏览器 13 个路由分页及顶部提示抽查                                      PASS
浏览器操作日志页搜索框右侧定位                                          PASS
git diff --check -- frontend/src/App.vue frontend/src/main.ts frontend/src/views frontend/e2e
                                                                      PASS
```

## 未完成与风险

- 尚待独立 Reviewer 复核，故任务状态为 `REVIEW`。
- 业务周、角色与物料分类原有页面没有分页组件；本次只统一已有分页的布局，未扩展其数据模型或 API。

## 决策/待确认

- 无。

## 下一步

- 独立复核页面布局与搜索行为，通过后更新本任务状态。
