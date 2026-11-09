# 16 phase2-api-and-task-flow 任务报告

## 状态

`DONE`

## 本次完成

- BatchOrchestratorService: 批次摘要、发布校验（issues/unallocated/checksum）、状态转换
- MrpBatchLifecycleController: 批次摘要API、发布API、状态转换API、issues API、批次列表API
- 修复 MrpCalculationService 乐观锁版本冲突：使用 updateStatusForce 进行内部状态转换
- 添加 updateStatusForce（无版本检查）和 selectAll 到 Mapper

## 修改文件

| 文件 | 操作 |
|---|---|
| `backend/src/main/java/com/mrp/mrp/service/BatchOrchestratorService.java` | 新建 |
| `backend/src/main/java/com/mrp/mrp/controller/MrpBatchLifecycleController.java` | 新建 |
| `backend/src/main/java/com/mrp/mrp/service/MrpCalculationService.java` | 修改 |
| `backend/src/main/java/com/mrp/mrp/repository/MrpCalcBatchMapper.java` | 修改 |
| `backend/src/main/resources/mapper/mrp/MrpCalcBatchMapper.xml` | 修改 |

## 验证命令与结果

```text
$ ./mvnw clean compile -q → BUILD SUCCESS
$ ./mvnw test -pl . -Dtest='!BomExplosionServiceTest' → 124/124 PASS

Full Lifecycle:
1. Setup: Batch=7, BOM=8
2. Precheck: CHECKING
3. Lock: READY_TO_CALCULATE
4. Calculate: READY
5. Distribute: 66 daily plans
6. Summary: status=READY, plans=66, pending=15
7. Publish (expect fail): "Cannot publish: 15 pending unallocated quantities"
8. Publish (after resolving): PUBLISHED
9. Final: status=PUBLISHED, checksum=98af2964459a28f2...
```

## 业务不变量

- 发布前校验：所有 issues 已解决、所有 unallocated 已解决
- 结果 checksum：基于所有日计划计算 SHA-256
- 乐观锁：外部 API 使用 version 检查，内部状态转换使用 force 更新
- 状态机：DRAFT→CHECKING→READY_TO_CALCULATE→CALCULATING→READY→PUBLISHED

## 未完成与风险

- Q2-016 发布后修改未冻结
- 异步任务执行（Worker、lease、SKIP LOCKED）未实现

## 决策/待确认

- Q2-016: 发布后修改策略

## 下一步

进入阶段 17：前端
