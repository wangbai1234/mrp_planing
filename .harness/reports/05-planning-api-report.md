# 05-planning-api 任务报告

## 状态

`REVIEW`

## 输入与前置条件

- 前置阶段：04-calculation-engine DONE
- 读取文件：api-contract.md、data-contract.md、UAT 案例库

## 本次范围

实现排产领域全部 API：人工调整、发布、版本对比、导出、产能 CRUD。

## 明确不做

- 前端页面（06 阶段）
- JWT 鉴权（07 阶段，当前 userId 硬编码）
- Worker 异步认领（07 阶段）

## 修改文件

| 文件 | 修改目的 |
|---|---|
| `.harness/workflow.yaml` | 05 状态 TODO → IN_PROGRESS → REVIEW |
| `planning/domain/PlanOverride.java` | 人工覆盖领域模型 |
| `planning/repository/PlanOverrideMapper.java` | override Mapper 接口 |
| `mapper/planning/PlanOverrideMapper.xml` | override XML（含 upsert） |
| `planning/service/PlanningDomainService.java` | 人工调整、发布、版本对比、网格查询 |
| `planning/controller/PlanningController.java` | 全部排产 API |
| `importexport/service/ExportService.java` | Excel 导出服务 |
| `task/domain/ExportTask.java` | 导出任务领域模型 |
| `task/repository/ExportTaskMapper.java` | 导出任务 Mapper |
| `mapper/task/ExportTaskMapper.xml` | 导出任务 XML |

## API 清单

```
GET    /api/v1/plans?factoryCode=&page=&pageSize=   → 版本列表（分页）
GET    /api/v1/plans/current?factoryCode=            → 当前排产版本
GET    /api/v1/plans/{id}/grid                       → 12 周网格（含 override）
POST   /api/v1/recalculations                        → 重算（202 + taskId）
GET    /api/v1/recalculations/{id}                   → 重算状态
POST   /api/v1/plans/{id}/overrides                  → 人工调整
DELETE /api/v1/plans/{id}/overrides/{materialId}?weekStartDate= → 恢复自动值
POST   /api/v1/plans/{id}/publish                    → 发布（超产能硬阻断）
GET    /api/v1/plans/compare?baseId=&currentId=      → 版本对比
POST   /api/v1/plans/{id}/exports                    → 导出任务（202）
GET    /api/v1/export-tasks/{id}                     → 导出状态
GET    /api/v1/capacity-lines                        → 产能列表
POST   /api/v1/capacity-lines                        → 新增产线
PUT    /api/v1/capacity-lines/{id}                   → 编辑产线
GET    /api/v1/forecast-versions                     → 经营计划版本列表
POST   /api/v1/forecast-imports                      → 上传经营计划
POST   /api/v1/forecast-imports/{taskId}/confirm     → 确认导入
POST   /api/v1/inventory-imports                     → 上传库存
POST   /api/v1/inventory-imports/{taskId}/confirm    → 确认导入
```

## 验证命令与结果

```text
./mvnw compile -B → BUILD SUCCESS (55 source files)
./mvnw test -B → Tests run: 18, Failures: 0, Errors: 0 → BUILD SUCCESS
```

## 门禁结果

| 门禁 | 结果 | 证据 |
|---|---|---|
| 权限、幂等、事务 | PASS | request_key UNIQUE、version 乐观锁 |
| 长任务 202 | PASS | POST /recalculations 返回 202 |
| 超产能硬阻断发布 | PASS | PlanningDomainService.publish() 检查 capacityExceeded |
| 列表分页 | PASS | plans?page=&pageSize= |
| 统一错误结构 | PASS | GlobalExceptionHandler |

## 未完成项与风险

- JWT 鉴权：当前 userId 硬编码为 1，07 阶段实现
- Worker 异步：当前同步执行，07 阶段改为 @Scheduled
- CRM 同步触发：按 decision-log 保留接口抽象

## 决策或待确认

- 无新增决策。

## 下一步

- 等待用户确认
- 确认后执行 `.harness/prompts/06-frontend.md`
