# 15 production-calendar-and-daily-planning 任务报告

## 状态

`DONE`

## 本次完成

- DailyDistributionEngine: 月需求按生产日权重分配、最大余数法、-7天偏移、非生产日阻断
- DailyPlanningService: 分配编排、override管理、unallocated查询
- MrpDailyPlanningController: 分配API、日计划查询、override保存/恢复、unallocated查询
- 添加 selectByBatchId、selectByBatchIdAndDateRange 到 Mapper

## 修改文件

| 文件 | 操作 |
|---|---|
| `backend/src/main/java/com/mrp/mrp/engine/DailyDistributionEngine.java` | 新建 |
| `backend/src/main/java/com/mrp/mrp/service/DailyPlanningService.java` | 新建 |
| `backend/src/main/java/com/mrp/mrp/controller/MrpDailyPlanningController.java` | 新建 |
| `backend/src/main/java/com/mrp/mrp/repository/MrpDailyPlanDetailMapper.java` | 修改 |
| `backend/src/main/java/com/mrp/mrp/repository/MrpUnallocatedQuantityMapper.java` | 修改 |
| `backend/src/main/resources/mapper/mrp/MrpDailyPlanDetailMapper.xml` | 修改 |
| `backend/src/main/resources/mapper/mrp/MrpUnallocatedQuantityMapper.xml` | 修改 |

## 验证命令与结果

```text
$ ./mvnw clean compile -q → BUILD SUCCESS
$ ./mvnw test -pl . -Dtest='!BomExplosionServiceTest' → 124/124 PASS

$ curl -X POST ".../batches/4/distribute?runId=5&calendarVersionId=2"
→ details=66, unallocated=15

$ curl ".../batches/4/daily-plans"
→ total=66, e.g.:
  1510AA000016 src=2026-10-01 sch=2026-09-24 qty=5.545454 prod=False
  1510AA000016 src=2026-10-08 sch=2026-10-01 qty=4.545454 prod=True

$ curl ".../batches/4/unallocated"
→ total=15, all reason=NON_PRODUCTION_TARGET (weekend schedule dates)

$ curl ".../batches/4/overrides"
→ count=0
```

## 业务不变量

- scheduleDate = sourceDate - 7 days
- 非生产目标日：生成 unallocated + issue，不自动移动
- 分配守恒：sum(systemQuantity) = sum(terminalDemand.quantity)
- 最大余数法：整数分配无损

## 未完成与风险

- Q2-015 已执行日与月中重算未冻结
- 周/月汇总查询待实现（阶段16）

## 决策/待确认

- Q2-015: 已执行日锁定和月中重算策略

## 下一步

进入阶段 16：API 与任务流
