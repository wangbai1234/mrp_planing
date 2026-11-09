# MRP API 契约（Phase 2）

## 1. 基础规则

- 前缀 `/api/v1`，JSON UTF-8，日期 `YYYY-MM-DD`，月份 `YYYY-MM`；
- 数量响应使用 decimal string，禁止依赖 JavaScript Number 表达 DECIMAL；
- 所有列表服务端分页，默认 20、最大 100；搜索/筛选后回到第 1 页；
- 长任务返回 `202 Accepted + taskId`；
- 写请求使用 `Idempotency-Key`；可并发资源使用 `expectedRevision` 或 `If-Match`；
- 每个请求由后端强制工厂数据域和权限码，不能只依赖前端隐藏；
- 统一错误体：

```json
{
  "code": "MRP_DECISION_REQUIRED",
  "message": "仍有 3 个阻断事项未处理",
  "details": [{"issueId": 12, "reason": "INVENTORY_MISSING"}],
  "traceId": "..."
}
```

乐观锁/状态并发冲突返回 409；未认证 401；无权限/越工厂域 403；参数格式校验 400；业务决策未解决、语义校验失败或无法发布返回 422，必须全局一致。

## 2. 配置版本

```text
GET/POST /mrp/category-role-versions
GET      /mrp/category-role-versions/{id}
PUT      /mrp/category-role-versions/{id}/details
POST     /mrp/category-role-versions/{id}/publish

GET/POST /mrp/material-policy-versions
GET      /mrp/material-policy-versions/{id}
PUT      /mrp/material-policy-versions/{id}/details
POST     /mrp/material-policy-versions/{id}/publish

GET/POST /production-calendar-versions
GET      /production-calendar-versions/{id}/days
PUT      /production-calendar-versions/{id}/days
POST     /production-calendar-versions/{id}/publish

GET/POST /mrp/factory-warehouse-mapping-versions
GET/PUT  /mrp/factory-warehouse-mapping-versions/{id}/details
POST     /mrp/factory-warehouse-mapping-versions/{id}/publish

GET/POST /mrp/inventory-control-policy-versions
GET/PUT  /mrp/inventory-control-policy-versions/{id}/details
POST     /mrp/inventory-control-policy-versions/{id}/publish

GET/POST /mrp/planning-week-versions
GET/PUT  /mrp/planning-week-versions/{id}/definitions
POST     /mrp/planning-week-versions/{id}/publish
```

发布版本不可原地编辑。列表支持状态、工厂、编码/料号关键词和生效时间筛选。

## 3. BOM 数据

新批次读取 BOM 管理中当前同步的全部有效关系，不选择或生成 BOM 版本快照；`is_default/HBDEFAULT` 不作为排产筛选条件。创建时记录内容校验码，预检、计算和发布前核对未变化。历史批次的快照接口只供追溯。创建页面通过 `GET /mrp/batches/current-bom-status` 检查当前 BOM 数据。

```text
POST /bom-snapshots
GET  /bom-snapshots/{id}
GET  /bom-snapshots/{id}/validation
```

上述快照接口描述为历史契约，新批次不调用创建接口。

## 4. 计算批次与决策

```text
POST /mrp/batches
GET  /mrp/batches
POST /mrp/batches/{id}/precheck
GET  /mrp/batches/{id}
GET  /mrp/batches/{id}/issues
PUT  /mrp/batches/{id}/issues/{issueId}/decision
POST /mrp/batches/{id}/issues/batch-decisions
POST /mrp/batches/{id}/lock-and-calculate
GET  /mrp/tasks/{taskId}
```

批次列表是进入详情的唯一默认入口，支持工厂范围、状态、创建时间、输入版本和关键词筛选。Phase 2 `planId` 与 `batchId` 是同一标识，`mrp_calc_batch` 是唯一排产版本，`mrp_batch_factory_scope` 是版本内多工厂计算范围；不使用旧 Phase 1 `plan_version` 作为新计划身份。

创建批次选择经营计划和一个统一库存快照版本；BOM 取当前同步数据并保存校验码。后端自动锁定已发布角色、物料策略、工厂仓库映射、库存控制策略、业务周期间，并根据经营计划中的工厂生成 factory scopes。UI 不手工传 BOM 快照、仓库映射或 `splitCategories`；任一必需配置不完整时拒绝创建。

## 5. 库存分配

```text
GET  /mrp/batches/{id}/inventory-pools
GET  /mrp/batches/{id}/inventory-pools/{poolId}/demands
POST /mrp/batches/{id}/inventory-pools/{poolId}/preview
PUT  /mrp/batches/{id}/inventory-pools/{poolId}/allocations
POST /mrp/batches/{id}/inventory-pools/{poolId}/confirm
```

系统先按来源月+经营计划 `prioritySeq` 生成 `AUTO_PRIORITY` 预览和分配记录。人工更改必须包含 `expectedRevision`、分配明细和原因；后端校验 `sum(allocated)+safetyReserved<=physicalGoodQty`。

## 6. 排产查询

```text
GET /mrp/plans
GET /mrp/plans/current?factoryCode=
GET /mrp/plans/{id}
GET /mrp/plans/{id}/daily-grid
GET /mrp/plans/{id}/weekly-summary
GET /mrp/plans/{id}/monthly-summary
GET /mrp/plans/{id}/requirement-traces
GET /mrp/plans/{id}/inventory-allocations
GET /mrp/plans/{id}/exceptions
GET /mrp/plans/compare?baseId=&currentId=
POST /mrp/plans/{id}/exports
GET /mrp/export-tasks/{taskId}
```

计划列表/当前计划是进入版本详情的入口。详情路由固定为 `/mrp/plans/:planId`，其中 `planId` 必须等于 `mrp_calc_batch.id`；内部使用“日排产、周汇总、月汇总、需求追溯、库存扣减”标签。`current` 返回全局唯一当前已发布版本及其工厂范围；`factoryCode` 只用于只读筛选范围，不产生每工厂独立的当前版本。

日网格参数至少包括：

```text
startDate, days(默认28，最大84), page, pageSize,
factoryCode, rootKeyword, terminalKeyword,
terminalType, supplyMode, adjustedOnly, issueOnly
```

日网格响应固定为：

```json
{
  "items": [{
    "rowKey": "sha256:...",
    "factoryCode": "F001",
    "rootMaterialCode": "A",
    "rootMaterialName": "整机A",
    "terminalMaterialCode": "D",
    "terminalMaterialName": "物料D",
    "terminalType": "RAW_MATERIAL",
    "supplyMode": "SELF_MADE",
    "pathGroupKey": "AGGREGATED",
    "cells": [{
      "scheduleDate": "2026-09-21",
      "systemQuantity": "10.000000",
      "manualQuantity": null,
      "effectiveQuantity": "10.000000",
      "isProductionDay": true,
      "overrideId": null,
      "revision": 0,
      "status": "AUTO",
      "blockedReason": null
    }]
  }],
  "total": 1,
  "page": 1,
  "pageSize": 20,
  "totalPages": 1,
  "calendarDays": [{"date":"2026-09-21","dayOfWeek":1,"isProductionDay":true}],
  "weekGroups": [{"businessMonth":"2026-10","weekCode":"W1","weekStart":"2026-09-28","weekEnd":"2026-10-04","dayIndexes":[0]}]
}
```

Phase 2 MVP 多路径主表按冻结规则汇总，`pathGroupKey` 固定为 `AGGREGATED`；`rowKey = SHA-256(batchId|factoryCode|rootMaterialCode|terminalMaterialCode|terminalType|supplyMode|pathGroupKey)`。路径只用独立 `pathHash` 出现在追溯接口。查询只读取 `mrp_calc_batch.active_run_id` 对应的 READY/PUBLISHED 结果；WRITING/FAILED run 不可见。

周汇总参数：`startBusinessMonth`、`weeks`（默认/最大12）及与日网格相同的行筛选和分页；响应包含 `weeks[{businessMonth,weekCode,weekStart,weekEnd}]`。点击周数量以相同 rowKey 和该周实际开始/结束日查询日网格，不假定固定 7 天。月汇总默认按业务月 W1-Wn 聚合，可以 `aggregation=NATURAL_MONTH` 查询另一个自然月只读口径。

## 7. 日调整和发布

```text
DELETE /mrp/plans/{id}/daily-overrides/{overrideId}
POST   /mrp/plans/{id}/daily-reallocations
POST   /mrp/plans/{id}/publish
```

覆盖键使用稳定 rowKey + scheduleDate；不得使用“物料+周”。MVP 不提供可绕过守恒的单日 PUT，只允许一次提交同一行的多日重分配，保持该行总量不变；跨周必须填写原因。发布返回所有阻断项，不做部分发布。

### 7.1 已执行/锁定和修订

```text
POST /mrp/plans/{id}/daily-execution
GET  /mrp/plans/{id}/daily-execution
POST /mrp/plans/{id}/revisions
```

执行确认接口只写独立执行事实，不修改日自动计划或发布结果 checksum。`LOCKED/EXECUTED` 日拒绝重算、重分配和修订覆盖；`EXECUTED` 必须提交实际完成量。实际量与有效计划量不一致时生成 `EXECUTION_QUANTITY_MISMATCH` 异常。

修订接口从已发布批次创建新批次，记录 `revisionOfBatchId` 和递增 `revisionNo`。已发布批次不可原地修改；新修订必须重新执行预检、计算、守恒和发布门禁。

### 7.2 库存快照字段

库存上传先创建快照组，再使用 `/inventory/snapshots/{id}/scopes/{warehouseCode}/upload` 为永惠、爱培科、惠州仓等范围分别导入。每份文件一仓库，明细增加 `inventoryQuality`，仅接受 `GOOD`/`DEFECTIVE`。查询返回仓库、品质、物理库存、安全库存、可分配量和预警状态。

## 8. 导入导出资源

需要模板导入的 Phase 2 资源统一提供：

```text
GET  /mrp/batches/{id}/decisions/template
POST /mrp/batches/{id}/decision-imports
GET  /mrp/decision-imports/{taskId}
GET  /mrp/decision-imports/{taskId}/error-report
POST /mrp/decision-imports/{taskId}/confirm

GET  /production-calendar-versions/{id}/template
POST /production-calendar-versions/{id}/imports
GET  /production-calendar-imports/{taskId}
GET  /production-calendar-imports/{taskId}/error-report
POST /production-calendar-imports/{taskId}/confirm
GET  /production-calendar-versions/{id}/export
```

全部遵守 `import-rules.md` 的 staging、rawRows/headers、错误报告和原子确认。若阶段 10 决定 MVP 不做某项导入，必须同步从页面需求和权限中删除，不能只做空按钮。

## 9. 权限码

权限码冻结为：

```text
mrp_batch:view/create/precheck/calculate
mrp_decision:view/edit/import
mrp_allocation:view/edit/confirm
daily_plan:view/adjust/publish/export
mrp_category_role:view/edit/publish
mrp_material_policy:view/edit/publish
production_calendar:view/edit/publish/import_export
factory_warehouse_mapping:view/edit/publish
inventory_control_policy:view/edit/publish/import_export
planning_week:view/edit/publish
```

Flyway 权限种子、后端 `@PreAuthorize`、前端 route meta、按钮守卫和 E2E 必须使用相同字符串。现有 `schedule:view` 只服务 Phase 1 兼容页面，不授权 Phase 2。

## 10. 前端精度

TypeScript 数量类型使用 `DecimalString`（品牌 string 或明确别名），编辑使用 `el-input` 文本与格式校验，不使用 `number`、`el-input-number`、`toLocaleString()` 或前端浮点运算处理正式数量。最终精度和守恒由后端校验，前端仅做即时格式提示。

## 11. 兼容

- Phase 1 `GET /plans/{id}/grid` 和历史版本查询继续只读；
- Phase 2 版本不得调用旧周 override；
- 新页面只调用 `/mrp/*` 日事实接口；
- 旧 `POST` 重算/`splitCategories` 写入口在 Phase 2 激活后返回明确 `410 GONE` 或 `409 PHASE2_ACTIVE`，不得静默写入旧表；只保留历史 GET 查询；
- 已出货 API 不属于本期门禁，任何 shipment 字段均不得改变 Phase 2 结果。


## 12. 2026-10-14 物料策略补充接口（用户确认）

```text
GET /mrp/material-policies/materials/page?keyword=&page=1&pageSize=10
GET /mrp/stock-policies/materials/page?keyword=&page=1&pageSize=10
GET /mrp/material-policies/page?keyword=&supplyMode=&explosionPolicy=&page=1&pageSize=20
GET /mrp/stock-policies/page?keyword=&warehouseCode=&page=1&pageSize=20
GET /mrp/batches/{id}/material-policy-notices?keyword=&missingType=&page=1&pageSize=20
```

- 两个物料选择接口查询真实 `material` 表，仅返回启用且未删除的物料；编码、名称、规格模糊匹配，响应为 `ApiResponse<PageResult>`。物料选择结果包含 `materialCode/materialName/specModel`。
- 新增、修改、Excel 导入、旧版本明细写入均校验有效物料；供应方式只能为 `SELF_MADE/PURCHASED/OUTSOURCED`，下钻策略只能为 `EXPLODE/STOP`，缺失或历史哨兵禁止作为新配置写入。
- 缺失清单读取批次 `active_run_id`，按工厂+物料去重，包含 `materialCode/materialName/factoryCode/missingType/effectiveExplosionPolicy/sourcesJson`；`missingType` 为 `BOTH/SUPPLY/EXPLOSION`。来源包含全部去重的整机、月份、BOM 路径。未切换到 active 的候选 run 不可见。
- 缺下钻策略默认 `EXPLODE`，有效 `STOP` 独立生效；清单仅提示，不生成阻断 issue。发布复制被冻结的清单，旧结果不回填。
- 缺失清单当前安全实现使用 `mrp_workspace:view` 或 `mrp_batch:list` 读取权限，并校验角色工厂数据域；无认证 401、无权限或越域 403、批次不存在 404。配置读取/管理权限按实际 SecurityConfig 的 `mrp_config:*`、`mrp_stock_policy:*` 执行。

## 2026-10-14 日产能 API 补充

- GET /mrp/factories：当前两工厂，独立于历史产线。
- GET /mrp/production-calendars/month、/page：按工厂/月（含未配置日期），列表服务端筛选分页。
- POST /mrp/production-calendars/batch/preview、/batch：日期范围≤366、星期1..7、保留特例默认、跨厂复制，提交校验预览fingerprint；单日PUT要求revision。
- 导入/导出六列：工厂编码、日期、生产日、日产能、单位、备注；整份预检，错误不写入。
- GET /mrp/plans/{id}/daily-grid：capacityLoads为完整批次工厂日负荷、activeRunId和冻结materialUnit；筛选分页只影响rows。
- GET /mrp/batches/{id}/capacity-loads?startDate&endDate：冻结能力/有效负荷/超出量/状态/部分未评估。
- POST /mrp/batches/{id}/daily-transfers：rowKey、sourceDate、targetDate、quantity、reason、expectedRunId、两端expectedQuantity/Revision，空端revision=-1。原子转出转入，不改变需求身份。
- GET /mrp/batches/{id}/capacity-review：loads+fingerprint。发布POST body.capacityFingerprint确认当前异常；计划变化需重新确认。

Quantity传输使用十进制字符串，前端BigInt六位定点运算，最大12位整数；服务端BigDecimal。日历功能遵循mrp_calendar:view/manage、转移mrp_plan:adjust。既有工厂数据域隔离债务见issue-log，不能把功能权限验证当作工厂域验收。

## 2026-10-14 整机能力接口（C019）

- daily-grid及周/月汇总增加可选view=MACHINE_OUTPUT|TERMINAL，新v3默认整机，历史默认末级；在分页前过滤行，capacityLoads始终按完整计划。
- daily-grid响应capacityBasis=MACHINE_OUTPUT|TERMINAL标明实际能力口径，与显示view区分；ruleVersion标明冻结版本。MACHINE_OUTPUT行materialUnit固定台。
- 日历新写入/批量/导入能力固定台，生产日正整数，停产0，保留十进制字符串传输。整机daily-transfers只接受整数台数，末级继续原六位小数规则。
- 周/月不混合整机与备料数量；发布和容量复核沿用原接口，新规则按整机事实评估，历史v2兼容。


## 2026-10-15 v4排产接口

新增 GET `/mrp/batches/{id}/rolling-grid`（page/pageSize/factoryCode/keyword；完整日期轴、视觉行服务端分页、未受物料筛选影响的工厂负荷）；GET `/mrp/batches/{id}/rolling-details`（日期范围、工厂、整机、末级、类型筛选及分页）；POST `/mrp/batches/{id}/linked-transfers/preview`；POST `/mrp/batches/{id}/linked-transfers`。联动输入包含rowKey/sourceDate/targetDate/quantity/expectedRunId，确认增加fingerprint/reason/lines[{rowKey,quantity}]，必须包括完整关联集合且全部转移量为整数。提交在一个事务中复核并写入，变化返回并发冲突。创建支持factoryCode=ALL及显式inventorySnapshotIds，冻结两厂输入。
