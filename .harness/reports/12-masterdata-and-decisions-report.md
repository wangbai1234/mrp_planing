# 12 masterdata-and-decisions 任务报告

## 状态

`DONE`

## 本次完成

- MrpConfigurationService: 分类角色、物料策略、生产日历版本 CRUD + 发布 + checksum
- DecisionService: 决策问题创建、记录决策（乐观锁 revision）、批量解决
- MrpConfigController: 全部配置 API
- MrpBatchController: 批次创建、预检、锁定计算
- GlobalExceptionHandler: 增加 IllegalStateException 处理器（返回 400 MRP_ILLEGAL_STATE）
- 修复领域实体类型不匹配：versionNo→Integer, createdBy→Long, operatorId→Long, sourceMonth→LocalDate, effectiveAt→LocalDate
- 修复 mapper XML version=#{version+1} → version=version+1
- 修复 MrpCalcBatch INSERT 缺少 rule_version 默认值

## 修改文件

| 文件 | 操作 |
|---|---|
| `backend/src/main/java/com/mrp/mrp/service/MrpConfigurationService.java` | 新建 |
| `backend/src/main/java/com/mrp/mrp/service/DecisionService.java` | 新建 |
| `backend/src/main/java/com/mrp/mrp/controller/MrpConfigController.java` | 新建 |
| `backend/src/main/java/com/mrp/mrp/controller/MrpBatchController.java` | 新建 |
| `backend/src/main/java/com/mrp/common/exception/GlobalExceptionHandler.java` | 修改 |
| `backend/src/main/java/com/mrp/mrp/domain/CategoryRoleVersion.java` | 修改（类型修复） |
| `backend/src/main/java/com/mrp/mrp/domain/MaterialPolicyVersion.java` | 修改（类型修复） |
| `backend/src/main/java/com/mrp/mrp/domain/ProductionCalendarVersion.java` | 修改（类型修复） |
| `backend/src/main/java/com/mrp/mrp/domain/MrpCalcBatch.java` | 修改（类型修复） |
| `backend/src/main/java/com/mrp/mrp/domain/MrpDecisionRecord.java` | 修改（类型修复） |
| `backend/src/main/java/com/mrp/mrp/domain/MrpDecisionIssue.java` | 修改（增加 updatedAt） |
| `backend/src/main/java/com/mrp/mrp/domain/MrpDailyPlanOverride.java` | 修改（类型修复） |
| `backend/src/main/java/com/mrp/mrp/domain/MrpInventoryAllocation.java` | 修改（sourceMonth→LocalDate） |
| `backend/src/main/java/com/mrp/mrp/domain/MrpRequirementDetail.java` | 修改（sourceMonth→LocalDate） |
| `backend/src/main/java/com/mrp/mrp/domain/MrpDailyPlanDetail.java` | 修改（sourceMonth→LocalDate） |
| `backend/src/main/java/com/mrp/mrp/domain/MrpTerminalDemand.java` | 修改（sourceMonth→LocalDate） |
| `backend/src/main/java/com/mrp/mrp/domain/BomSnapshot.java` | 修改（effectiveAt→LocalDate） |
| `backend/src/main/resources/mapper/mrp/MrpCalcBatchMapper.xml` | 修改（version+1 修复） |

## 验证命令与结果

```text
$ ./mvnw clean compile -q
BUILD SUCCESS

$ ./mvnw test -pl . -Dtest='!BomExplosionServiceTest' -q
Tests run: 124, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

$ curl -X POST http://localhost:8081/api/v1/mrp/category-role-versions -H "Authorization: Bearer $TOKEN"
→ {"success":true,"data":{"id":1,"versionNo":1204302470,"status":"DRAFT",...}}

$ curl http://localhost:8081/api/v1/mrp/category-role-versions -H "Authorization: Bearer $TOKEN"
→ {"success":true,"data":[...]}

$ curl -X POST http://localhost:8081/api/v1/mrp/category-role-versions/2/details -H "Authorization: Bearer $TOKEN" -d '[{"categoryCode":"CAT001","role":"FINISHED_GOOD","enabled":true}]'
→ {"success":true}

$ curl http://localhost:8081/api/v1/mrp/category-role-versions/2/details -H "Authorization: Bearer $TOKEN"
→ {"success":true,"data":[{"categoryCode":"CAT001","role":"FINISHED_GOOD","enabled":true}]}

$ curl -X POST http://localhost:8081/api/v1/mrp/category-role-versions/2/publish -H "Authorization: Bearer $TOKEN"
→ {"success":true}

$ curl -X POST http://localhost:8081/api/v1/mrp/category-role-versions/2/details ... (after publish)
→ {"success":false,"error":{"code":"MRP_ILLEGAL_STATE","message":"Version not found or not in DRAFT status"}}

$ curl -X POST "http://localhost:8081/api/v1/mrp/batches?factoryCode=F001" -H "Authorization: Bearer $TOKEN"
→ {"success":true,"data":{"status":"DRAFT","ruleVersion":"v2",...}}

$ curl -X POST http://localhost:8081/api/v1/mrp/batches/1/precheck -H "Authorization: Bearer $TOKEN"
→ {"success":true,"data":{"status":"CHECKING",...}}

$ curl -X POST http://localhost:8081/api/v1/mrp/batches/1/lock-and-calculate -H "Authorization: Bearer $TOKEN"
→ {"success":true,"data":{"status":"READY_TO_CALCULATE","version":1,...}}
```

## 业务不变量

- 发布后不可编辑：已验证（publish 后 save details 返回 MRP_ILLEGAL_STATE）
- 乐观锁：MrpCalcBatch.update 使用 WHERE version=#{version}，SET version=version+1
- checksum：publish 时计算 SHA-256

## 未完成与风险

- 无

## 决策/待确认

- 无

## 下一步

进入阶段 13：库存规范化
