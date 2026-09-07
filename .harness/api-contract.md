# MRP API 契约

## 1. 基础规则

- 前缀：`/api/v1`；
- JSON UTF-8；时间 ISO-8601；月份 `YYYY-MM`；周日期 `YYYY-MM-DD`；
- 列表接口必须分页，默认 `pageSize <= 100`；排产网格必须支持工厂、料号、异常筛选；
- 长任务返回 `202 Accepted` 和任务号；不能同步等待排产完成；
- 写请求支持 `Idempotency-Key`；并发修改使用 `If-Match` 或版本号；
- 统一错误结构：

```json
{
  "code": "MRP_VALIDATION_ERROR",
  "message": "来源月份 2026-09 尚差 20",
  "details": [{"field":"week_start_date","reason":"MONTH_IMBALANCE"}],
  "traceId": "..."
}
```

## 2. 最小资源

```text
POST /auth/login
GET  /me

GET  /forecast-versions
POST /forecast-imports
GET  /import-tasks/{id}
POST /forecast-imports/{id}/confirm

GET  /inventory-snapshots
POST /inventory-imports
POST /shipment-sync-tasks

GET  /capacity-lines
POST /capacity-lines
PUT  /capacity-lines/{id}

GET  /plans/current
GET  /plans/{id}/grid
POST /recalculations
GET  /recalculations/{id}
POST /plans/{id}/overrides
DELETE /plans/{id}/overrides/{overrideId}
POST /plans/{id}/publish
GET  /plans/compare?baseId=&currentId=
POST /plans/{id}/exports
GET  /export-tasks/{id}
```

## 3. 查询和结果

`GET /plans/{id}/grid` 必须返回周元数据：`weekStartDate`、`weekEndDate`、`physicalMonth`、`sourceMonth`、`slot`、`isCarry`、`isLocked`，以及每个单元格的自动值、人工值、最终值、超限差额和状态。

## 4. 权限

后端每个查询都必须带工厂数据域过滤，不能只依赖前端下拉框。导入、人工调整、发布和产能配置分别检查角色权限。
