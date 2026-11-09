# 17 phase2-frontend 任务报告

## 状态

`DONE`

## 本次完成

- `frontend/src/api/mrp.ts`: 全部 Phase 2 API 类型定义和函数
- `frontend/src/views/mrp/MrpBatchList.vue`: 批次列表页面（状态筛选、创建、预检、发布）
- `frontend/src/views/mrp/MrpBatchDetail.vue`: 批次详情页面（摘要卡片、需求明细、末级需求、日排产、问题、待分配）
- 路由配置：/mrp/batches、/mrp/batches/:id
- 菜单配置：MRP批次（计划分组）
- 页面标签配置

## 修改文件

| 文件 | 操作 |
|---|---|
| `frontend/src/api/mrp.ts` | 新建 |
| `frontend/src/views/mrp/MrpBatchList.vue` | 新建 |
| `frontend/src/views/mrp/MrpBatchDetail.vue` | 新建 |
| `frontend/src/router/index.ts` | 修改 |
| `frontend/src/App.vue` | 修改 |
| `frontend/src/config/pageLabels.ts` | 修改 |

## 验证命令与结果

```text
$ npm run build → built in 379ms ✓
```

## 页面功能

### MrpBatchList
- 批次列表（ID、工厂、状态、版本号、问题数、创建时间、发布时间）
- 状态筛选（DRAFT/CHECKING/READY/PUBLISHED）
- 新增批次弹窗
- 预检操作
- 发布操作（带确认）

### MrpBatchDetail
- 摘要卡片（状态、日计划数、待分配数、库存池数、可用库存）
- 需求明细表格（层级、物料、毛需求、库存、净需求、末级标记）
- 末级需求表格（物料、类型、供应方式、来源月、数量）
- 日排产表格（物料、来源日、排产日、数量、生产日标记）
- 问题表格（问题键、类型、严重度、状态）
- 待分配表格（需求键、来源日、目标日、数量、原因、状态）

## 未完成与风险

- 日历配置页面未实现
- 库存分配工作台页面未实现
- 周/月汇总视图未实现
- Playwright E2E 测试未实现

## 下一步

进入阶段 18：集成/性能/安全
