# 04-calculation-engine 任务报告

## 状态

`REVIEW`

## 输入与前置条件

- 前置阶段：03-import-and-masterdata DONE
- 读取文件：calculation-contract.md、task-execution-contract.md、redis-contract.md、code-standards.md、UAT 案例库

## 本次范围

实现排产计算领域纯函数和 Spring Batch 任务：月可排量、拆周余数前置、提前量、12 周窗口、产能超限校验、任务认领。

## 明确不做

- 人工调整 API（05 阶段）
- 发布 API（05 阶段）
- 前端页面（06 阶段）
- Worker 异步认领（当前同步执行，07 阶段完善）

## 修改文件

| 文件 | 修改目的 |
|---|---|
| `.harness/workflow.yaml` | 04 状态 TODO → IN_PROGRESS → REVIEW |
| `planning/domain/WeekSlot.java` | 周槽位领域模型 |
| `planning/domain/WeekPlan.java` | 周计划领域模型（含人工覆盖） |
| `planning/domain/MaterialPlanResult.java` | 物料排产结果 |
| `planning/domain/PlanningCalculator.java` | 核心纯函数（无 Spring/DB 依赖） |
| `planning/domain/PlanVersion.java` | 排产版本领域模型 |
| `planning/domain/PlanDetail.java` | 排产明细领域模型 |
| `planning/repository/PlanMapper.java` | MyBatis Mapper 接口 |
| `mapper/planning/PlanMapper.xml` | MyBatis XML SQL |
| `planning/service/PlanningService.java` | 排产计算服务 |
| `planning/controller/PlanningController.java` | 排产 API |
| `task/domain/CalcTask.java` | 计算任务领域模型 |
| `task/repository/CalcTaskMapper.java` | 任务 Mapper |
| `mapper/task/CalcTaskMapper.xml` | 任务 XML SQL |
| `planning/PlanningCalculatorTest.java` | 15 个黄金数据测试 |

## 黄金数据测试覆盖

| 测试 | 规则 | 验证 |
|---|---|---|
| monthlyAvailable_normal | R001 | 1000-200-100=700 |
| monthlyAvailable_inventoryExceedsDemand | R001 | 500-600-0=0 |
| monthlyAvailable_negativeResultClampedToZero | R013 | MAX(0, 负数)=0 |
| splitQuantities_900 | R002 | 300+200+200+200=900 |
| splitQuantities_1000_remainderToFront | R002 | 余数前置 |
| splitQuantities_1200 | R002 | 400+267+267+266=1200 |
| splitQuantities_conservation | R002 | 0..10000 全部守恒 |
| splitMonthToWeeks_aug2026 | R003 | carry 在 7 月 W4，ordinary 在 8 月 W1-W3 |
| generate12WeekPlan_shouldProduce12Weeks | R005 | 固定 12 周，周一，守恒 |
| checkCapacity_shouldMarkExceeded | R014 | 超限标记和差额正确 |
| computeInputChecksum | 可重复性 | 相同输入 → 相同 checksum |

## 验证命令与结果

```text
./mvnw compile -B → BUILD SUCCESS
./mvnw test -B → Tests run: 18, Failures: 0, Errors: 0 → BUILD SUCCESS
```

## 门禁结果

| 门禁 | 结果 | 证据 |
|---|---|---|
| 黄金数据测试通过 | PASS | 15 个 PlanningCalculatorTest |
| 守恒校验 | PASS | splitQuantities_conservation |
| 可重复性 | PASS | computeInputChecksum |
| 月中锁定 | PASS | isWeekLocked 逻辑 |
| 不在 Controller/Vue 做公式 | PASS | 纯函数在 PlanningCalculator |

## 未完成项与风险

- Worker 异步认领：当前同步执行，后续改为 @Scheduled 轮询 + Redisson 锁
- 月中重算锁定范围：当前按 currentWeekStart 前的周锁定，完整月中重算逻辑待 05 阶段
- 人工覆盖保留：当前不保留旧版本覆盖，05 阶段完善

## 决策或待确认

- 无新增决策。

## 下一步

- 等待用户确认
- 确认后执行 `.harness/prompts/05-planning-api.md`
