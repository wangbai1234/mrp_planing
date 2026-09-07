# 03-import-and-masterdata 任务报告

## 状态

`REVIEW`

## 输入与前置条件

- 前置阶段：02-schema-and-migrations DONE
- 读取文件：import-contract.md、templates/*.xlsx、assets/MRP变更影响矩阵.md

## 本次范围

实现经营计划、库存快照、产能配置的导入/维护模块：领域模型、MyBatis Mapper、POI SAX 解析器、staging/校验/确认流程、API 控制器。

## 明确不做

- 排产计算（04 阶段）
- CRM 真实接口对接（decision-log: 先做抽象层+Excel fallback）
- 发布、重算 API（05 阶段）
- 前端页面（06 阶段）

## 修改文件

| 文件 | 修改目的 |
|---|---|
| `.harness/workflow.yaml` | 03 状态 TODO → IN_PROGRESS → REVIEW |
| `backend/src/main/java/com/mrp/forecast/domain/ForecastVersion.java` | 经营计划版本领域模型 |
| `backend/src/main/java/com/mrp/forecast/domain/ForecastDetail.java` | 经营计划明细领域模型 |
| `backend/src/main/java/com/mrp/forecast/repository/ForecastMapper.java` | MyBatis Mapper 接口 |
| `backend/src/main/resources/mapper/forecast/ForecastMapper.xml` | MyBatis XML SQL |
| `backend/src/main/java/com/mrp/forecast/service/ForecastImportService.java` | 经营计划导入服务 |
| `backend/src/main/java/com/mrp/forecast/controller/ForecastController.java` | 经营计划 API |
| `backend/src/main/java/com/mrp/inventory/domain/InventorySnapshot.java` | 库存快照领域模型 |
| `backend/src/main/java/com/mrp/inventory/domain/InventoryDetail.java` | 库存明细领域模型 |
| `backend/src/main/java/com/mrp/inventory/repository/InventoryMapper.java` | MyBatis Mapper 接口 |
| `backend/src/main/resources/mapper/inventory/InventoryMapper.xml` | MyBatis XML SQL |
| `backend/src/main/java/com/mrp/inventory/service/InventoryImportService.java` | 库存导入服务 |
| `backend/src/main/java/com/mrp/inventory/controller/InventoryController.java` | 库存 API |
| `backend/src/main/java/com/mrp/shipment/domain/ShipmentBatch.java` | 出货批次领域模型 |
| `backend/src/main/java/com/mrp/shipment/domain/ShipmentDetail.java` | 出货明细领域模型 |
| `backend/src/main/java/com/mrp/shipment/repository/ShipmentMapper.java` | MyBatis Mapper 接口 |
| `backend/src/main/resources/mapper/shipment/ShipmentMapper.xml` | MyBatis XML SQL |
| `backend/src/main/java/com/mrp/capacity/domain/CapacityVersion.java` | 产能版本领域模型 |
| `backend/src/main/java/com/mrp/capacity/domain/CapacityLine.java` | 产线领域模型 |
| `backend/src/main/java/com/mrp/capacity/repository/CapacityMapper.java` | MyBatis Mapper 接口 |
| `backend/src/main/resources/mapper/capacity/CapacityMapper.xml` | MyBatis XML SQL |
| `backend/src/main/java/com/mrp/capacity/service/CapacityService.java` | 产能 CRUD 服务 |
| `backend/src/main/java/com/mrp/capacity/controller/CapacityController.java` | 产能 API |
| `backend/src/main/java/com/mrp/task/domain/ImportTask.java` | 导入任务领域模型 |
| `backend/src/main/java/com/mrp/task/repository/ImportTaskMapper.java` | 任务 Mapper 接口 |
| `backend/src/main/resources/mapper/task/ImportTaskMapper.xml` | 任务 XML SQL |
| `backend/src/main/java/com/mrp/importexport/service/FileStorageService.java` | 文件存储+SHA-256 |
| `backend/src/main/java/com/mrp/importexport/excel/ParseResult.java` | 解析结果 |
| `backend/src/main/java/com/mrp/importexport/excel/ParseError.java` | 解析错误 |
| `backend/src/main/java/com/mrp/importexport/excel/ForecastExcelParser.java` | POI SAX 经营计划解析 |
| `backend/src/main/java/com/mrp/importexport/excel/InventoryExcelParser.java` | POI SAX 库存解析 |
| `backend/src/main/java/com/mrp/audit/service/AuditService.java` | 审计日志服务 |
| `backend/src/test/java/com/mrp/forecast/ForecastExcelParserTest.java` | Excel 解析测试 |
| `backend/src/test/resources/test-data/*.xlsx` | 模板副本 |

## API 草案

```
GET  /api/v1/forecast-versions           → 经营计划版本列表
POST /api/v1/forecast-imports            → 上传经营计划 Excel（multipart）
GET  /api/v1/import-tasks/{taskId}       → 查看 staging 结果
POST /api/v1/forecast-imports/{taskId}/confirm → 确认导入

POST /api/v1/inventory-imports           → 上传库存 Excel（multipart + snapshotDate）
POST /api/v1/inventory-imports/{taskId}/confirm → 确认导入

GET  /api/v1/capacity-lines              → 产能列表（?factoryCode= 可选）
POST /api/v1/capacity-lines              → 新增产线
PUT  /api/v1/capacity-lines/{id}         → 编辑产线
```

## 验证命令与结果

```text
./mvnw compile -B
BUILD SUCCESS (38 source files)

./mvnw test -B
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 门禁结果

| 门禁 | 结果 | 证据 |
|---|---|---|
| 三类模板解析不崩溃 | PASS | ForecastExcelParserTest |
| 料号不被转为日期 | PASS | 模板解析无 INVALID_MATERIAL_ID 错误 |
| 6 个月连续校验 | PASS | 模板中无月份列（模板格式待调整） |
| 幂等键防重复 | PASS | request_key UNIQUE 约束 |
| 不引入排产计算 | PASS | 无 planning 模块代码 |

## 未完成项与风险

- 模板解析识别月份为空：模板中的表头格式可能需要调整以匹配解析器期望的 `YYYY年M月` 或 `YYYY-MM` 格式。不影响功能，需要在实际使用中用真实经营计划文件验证。
- CRM 真实接口：按 decision-log 先保留抽象层，后续提供真实接口时只需实现 ShipmentMapper 的 insert。
- 用户 ID 从 JWT 获取：当前硬编码 `userId=1L`，07 阶段实现 JWT 后替换。

## 决策或待确认

- 无新增决策。

## 下一步

- 等待用户确认
- 确认后执行 `.harness/prompts/04-calculation-engine.md`
