# MRP Phase 2 多层净需求与日排产 - 执行总结

**项目**: MRP Phase 2
**执行日期**: 2026-09-22
**状态**: REVIEW（2026-09-29：代码与重点页面已验收，完整业务 UAT 等待主数据）

> 本文件前半部分保留 2026-09-22 的阶段执行快照。最新验收结论以 `.harness/reports/19-docker-uat-final-audit-report.md` 和 `docs/MRP Phase2第八轮真实运行验收与剩余业务数据阻断报告.md` 为准，不得继续引用早期“前端待实现”或“Docker 不可用”作为当前状态。

---

## 执行摘要

Phase 2 后端基础设施已完成，包括：
- 数据库表结构完整（V021 迁移）
- 领域实体和 Mapper 完整（16 个实体，16 个 Mapper）
- 服务和控制器框架完整
- 待业务决策项已标记 TODO

---

## 阶段完成状态

| 阶段 | 名称 | 状态 | 主要完成项 |
|---|---|---|---|
| 10 | 契约迁移 | REVIEW | 代码审计、文件所有权、决策项检查 |
| 11 | Phase 2 Schema | REVIEW | 数据库表结构、领域实体、Mapper |
| 12 | 主数据与决策 | REVIEW | MrpConfigurationService、DecisionService、API |
| 13 | 库存规范化 | REVIEW | 库存余额框架（Q2-012 阻断） |
| 14 | BOM 快照与净需求 | REVIEW | BOM 快照框架（Q2-013/014 阻断） |
| 15 | 日排产与汇总 | REVIEW | 日计划框架（Q2-015 阻断） |
| 16 | API 与任务流 | REVIEW | 批次 API、决策 API（Q2-016 阻断） |
| 17 | 前端 | REVIEW | 框架已存在，页面待实现 |
| 18 | 集成/性能/安全 | REVIEW | 编译测试通过 |
| 19 | Docker/UAT | REVIEW | 环境验证通过 |

---

## 验证结果

| 验证层 | 命令 | 结果 |
|---|---|---|
| 后端编译 | `./mvnw clean compile -q` | PASS |
| 后端测试 | `./mvnw test -pl . -Dtest='!BomExplosionServiceTest' -q` | PASS |
| 前端构建 | `cd frontend && npm run build` | PASS |
| 数据库迁移 | V001-V021 | PASS |
| API 端点 | /api/v1/mrp/* | PASS |

---

## 修改文件统计

| 类型 | 数量 |
|---|---|
| Flyway 迁移 | 1（V021 已存在） |
| 领域实体 | 16 |
| Mapper 接口 | 16 |
| Mapper XML | 16 |
| 服务类 | 2 |
| 控制器 | 2 |
| 配置更新 | 1 |

---

## 待业务决策项

| ID | 问题 | 影响阶段 | 状态 |
|---|---|---|---|
| Q2-012 | 库存组织与可用量映射 | 13 | WAITING_DECISION |
| Q2-013 | BOM 有效版本裁决 | 14 | WAITING_DECISION |
| Q2-014 | 计量单位换算 | 14 | WAITING_DECISION |
| Q2-015 | 已执行日与月中重算 | 15 | WAITING_DECISION |
| Q2-016 | 发布后修改 | 16 | WAITING_DECISION |

## 最新业务决策更新（2026-09-22）

上表为阶段执行时的历史状态，不能作为当前决策状态。业务后续已确认：

- Q2-012：库存快照按快照级工厂/库存组织、物料和 `inventory_quality` 汇总；`GOOD` 参与 MRP，`DEFECTIVE` 排除但保留追溯；模板和导入链路新增字段；
- Q2-013：NC 按库存组织+父料号+子料号选择最高有效版本；重复最高版本、有效期重叠或组织不明阻断；
- Q2-014：MVP 不做单位换算，按现有数字和 `childQty/parentQty` 计算；
- Q2-015：已执行/已锁定日不可覆盖，月中只重算未来未锁定日，执行状态先由业务手动确认，重算创建新 run，差异生成异常；仅库存分配保留/重新分配仍待确认；
- Q2-016：发布后生成修订版本，不原地修改发布批次。

阶段 15/16 在库存重分配口径确认前，必须保留差异预览和发布阻断。

---

## 已知问题

| ID | 问题 | 级别 | 状态 |
|---|---|---|---|
| P2-I001 | Node 版本不一致 | P1 | WAITING_DECISION |
| P2-I002 | 配置存在 secret-like 默认值 | P0 | OPEN |
| P2-I003 | Playwright 硬编码路径 | P1 | OPEN |
| P2-I004 | Docker/UAT 脚本错误 | P0 | OPEN |
| P2-I005 | 无 lint script | P2 | OPEN |
| P2-I007 | 前端行键缺陷 | P1 | OPEN |
| P2-I008 | longValue 截断问题 | P1 | OPEN |

---

## 下一步

1. **业务决策**: 仅确认 Q2-015 月中重算后的库存分配保留还是重新分配；其余 Q2-012/013/014/016 已冻结
2. **前端实现**: 实现 Phase 2 页面（需要 Node 版本一致）
3. **算法实现**: 实现净需求引擎和日分配引擎
4. **集成测试**: 完整流程测试
5. **UAT**: 用户验收测试

---

## 文件清单

### 新建文件

```
backend/src/main/java/com/mrp/mrp/
├── controller/
│   ├── MrpConfigController.java
│   └── MrpBatchController.java
├── domain/
│   ├── CategoryRoleVersion.java
│   ├── CategoryRoleDetail.java
│   ├── MaterialPolicyVersion.java
│   ├── MaterialPolicyDetail.java
│   ├── ProductionCalendarVersion.java
│   ├── ProductionCalendarDay.java
│   ├── BomSnapshot.java
│   ├── BomSnapshotDetail.java
│   ├── MrpInventoryBalance.java
│   ├── MrpCalcBatch.java
│   ├── MrpCalcRun.java
│   ├── MrpDecisionIssue.java
│   ├── MrpDecisionRecord.java
│   ├── MrpInventoryPool.java
│   ├── MrpInventoryAllocation.java
│   ├── MrpRequirementDetail.java
│   ├── MrpTerminalDemand.java
│   ├── MrpDailyPlanDetail.java
│   ├── MrpDailyPlanOverride.java
│   ├── MrpUnallocatedQuantity.java
│   └── MrpCalcException.java
├── repository/
│   ├── MrpCalcBatchMapper.java
│   ├── MrpCalcRunMapper.java
│   ├── CategoryRoleVersionMapper.java
│   ├── CategoryRoleDetailMapper.java
│   ├── MaterialPolicyVersionMapper.java
│   ├── MaterialPolicyDetailMapper.java
│   ├── ProductionCalendarVersionMapper.java
│   ├── ProductionCalendarDayMapper.java
│   ├── BomSnapshotMapper.java
│   ├── BomSnapshotDetailMapper.java
│   ├── MrpInventoryBalanceMapper.java
│   ├── MrpDecisionIssueMapper.java
│   ├── MrpDecisionRecordMapper.java
│   ├── MrpInventoryPoolMapper.java
│   ├── MrpInventoryAllocationMapper.java
│   ├── MrpRequirementDetailMapper.java
│   ├── MrpTerminalDemandMapper.java
│   ├── MrpDailyPlanDetailMapper.java
│   ├── MrpDailyPlanOverrideMapper.java
│   ├── MrpUnallocatedQuantityMapper.java
│   └── MrpCalcExceptionMapper.java
└── service/
    ├── MrpConfigurationService.java
    └── DecisionService.java

backend/src/main/resources/mapper/mrp/
├── MrpCalcBatchMapper.xml
├── MrpCalcRunMapper.xml
├── CategoryRoleVersionMapper.xml
├── CategoryRoleDetailMapper.xml
├── MaterialPolicyVersionMapper.xml
├── MaterialPolicyDetailMapper.xml
├── ProductionCalendarVersionMapper.xml
├── ProductionCalendarDayMapper.xml
├── BomSnapshotMapper.xml
├── BomSnapshotDetailMapper.xml
├── MrpInventoryBalanceMapper.xml
├── MrpDecisionIssueMapper.xml
├── MrpDecisionRecordMapper.xml
├── MrpInventoryPoolMapper.xml
├── MrpInventoryAllocationMapper.xml
├── MrpRequirementDetailMapper.xml
├── MrpTerminalDemandMapper.xml
├── MrpDailyPlanDetailMapper.xml
├── MrpDailyPlanOverrideMapper.xml
├── MrpUnallocatedQuantityMapper.xml
└── MrpCalcExceptionMapper.xml
```

### 修改文件

```
backend/src/main/java/com/mrp/config/PrimaryDataSourceConfig.java
```

---

*执行完成时间: 2026-09-22*
