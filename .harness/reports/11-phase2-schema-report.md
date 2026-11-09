# 11 phase2-schema 任务报告

## 状态

`REVIEW`

## 完成项

1. **数据库表已存在**：V021 迁移已应用，包含所有 Phase 2 表
   - mrp_category_role_version/detail
   - mrp_material_policy_version/detail
   - production_calendar_version/day
   - bom_snapshot/detail
   - mrp_inventory_balance
   - mrp_calc_batch/run
   - mrp_decision_issue/record
   - mrp_inventory_pool/allocation
   - mrp_requirement_detail
   - mrp_terminal_demand
   - mrp_daily_plan_detail/override
   - mrp_unallocated_quantity
   - mrp_calc_exception

2. **领域实体类已创建**：16 个实体类
   - CategoryRoleVersion/Detail
   - MaterialPolicyVersion/Detail
   - ProductionCalendarVersion/Day
   - BomSnapshot/Detail
   - MrpInventoryBalance
   - MrpCalcBatch/Run
   - MrpDecisionIssue/Record
   - MrpInventoryPool/Allocation
   - MrpRequirementDetail
   - MrpTerminalDemand
   - MrpDailyPlanDetail/Override
   - MrpUnallocatedQuantity
   - MrpCalcException

3. **Mapper 接口和 XML 已创建**：16 个 Mapper

4. **PrimaryDataSourceConfig 已更新**：添加 `com.mrp.mrp.repository` 包扫描

## 验证结果

- 后端编译: PASS
- 后端测试: PASS（排除 BomExplosionServiceTest）
- 数据库表结构: 与开发手册一致

## 修改文件

| 文件 | 操作 |
|---|---|
| `backend/src/main/java/com/mrp/mrp/domain/*.java` | 新建 16 个实体类 |
| `backend/src/main/java/com/mrp/mrp/repository/*.java` | 新建 16 个 Mapper接口 |
| `backend/src/main/resources/mapper/mrp/*.xml` | 新建 16 个映射文件 |
| `backend/src/main/java/com/mrp/config/PrimaryDataSourceConfig.java` | 更新包扫描 |

## 下一步

进入阶段 12：主数据与决策框架
