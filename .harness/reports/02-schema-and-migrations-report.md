# 02-schema-and-migrations 任务报告

## 状态

`REVIEW`

## 输入与前置条件

- 前置阶段：01-backend-bootstrap DONE
- 读取文件：data-contract.md、calculation-contract.md、task-execution-contract.md、code-standards.md
- 已有迁移：V001__init_schema.sql（01 阶段创建）

## 本次范围

对照契约审查 V001 迁移完整性，验证空库迁移和幂等性，输出 ER 说明和字段字典。

## 明确不做

- 业务模块 Java 代码（03 阶段）
- MyBatis Mapper（03 阶段）

## 修改文件

| 文件 | 修改目的 |
|---|---|
| `.harness/workflow.yaml` | 02 状态 TODO → IN_PROGRESS → REVIEW |

V001 迁移在 01 阶段已创建且覆盖完整，本次无需新增迁移文件。

## ER 说明

```
user ──1:N── user_role ──N:1── role
  │
  └──1:N── user_factory_scope (factory_code)

forecast_version ──1:N── forecast_detail (version_id, material_id, plan_month)

inventory_snapshot ──1:N── inventory_detail (snapshot_id, factory_code, material_id)

shipment_batch ──1:N── shipment_detail (batch_id, material_id, plan_month)

capacity_version ──1:N── capacity_line (version_id, factory_code, line_code)

plan_version ──1:N── plan_detail (plan_version_id, factory_code, material_id, week_start_date)
  │                    │
  │                    └── 1:1 plan_override (plan_version_id, material_id, week_start_date)
  │
  ├── N:1 forecast_version (forecast_version_id)
  ├── N:1 inventory_snapshot (inventory_snapshot_id) [nullable]
  ├── N:1 shipment_batch (shipment_batch_id) [nullable]
  └── N:1 capacity_version (capacity_version_id) [nullable]

import_task / calc_task / export_task  (独立任务表，通过 result_resource_id 关联业务实体)

audit_log (独立审计表，通过 resource_type + resource_id 关联任意实体)
```

## 字段字典（关键字段）

### plan_version（排产版本）

| 字段 | 类型 | 说明 | 约束 |
|---|---|---|---|
| id | BIGINT | 主键 | PK, AUTO_INCREMENT |
| version_no | INT | 版本号 | NOT NULL |
| factory_code | VARCHAR(32) | 工厂编码 | NOT NULL, INDEXED |
| forecast_version_id | BIGINT | 绑定经营计划版本 | NOT NULL, FK |
| inventory_snapshot_id | BIGINT | 绑定库存快照 | NULLABLE, FK |
| shipment_batch_id | BIGINT | 绑定出货批次 | NULLABLE, FK |
| capacity_version_id | BIGINT | 绑定产能版本 | NULLABLE, FK |
| rule_version | VARCHAR(32) | 规则版本 | NOT NULL, DEFAULT 'v1' |
| current_week_start | DATE | 当前周（周一） | NOT NULL |
| input_checksum | VARCHAR(64) | 输入校验和 | NULLABLE |
| result_checksum | VARCHAR(64) | 结果校验和 | NULLABLE |
| status | VARCHAR(32) | CALCULATING/READY/PUBLISHED/FAILED | NOT NULL, INDEXED |
| auto_recalc_forecast | TINYINT(1) | 经营计划导入后自动重算 | DEFAULT 0 |
| auto_recalc_inventory | TINYINT(1) | 库存导入后自动重算 | DEFAULT 0 |

### plan_detail（排产周结果）

| 字段 | 类型 | 说明 | 约束 |
|---|---|---|---|
| plan_version_id | BIGINT | 所属版本 | NOT NULL, FK, INDEXED |
| factory_code | VARCHAR(32) | 工厂编码 | NOT NULL |
| material_id | VARCHAR(64) | 整机料号 | NOT NULL |
| week_start_date | DATE | 周开始日期（周一） | NOT NULL, INDEXED |
| physical_month | DATE | 物理月份（每月1号） | NOT NULL |
| source_month | DATE | 来源月份（每月1号） | NOT NULL |
| slot | TINYINT | 0=提前量, 1..3=普通周 | NOT NULL |
| is_carry | TINYINT(1) | 是否提前量 | DEFAULT 0 |
| is_locked | TINYINT(1) | 已执行周锁定 | DEFAULT 0 |
| system_quantity | BIGINT | 系统自动值 | DEFAULT 0 |
| manual_quantity | BIGINT | 人工覆盖值 | NULLABLE |
| effective_quantity | BIGINT | 最终生效值 | DEFAULT 0 |
| capacity_exceeded | TINYINT(1) | 是否超产能 | DEFAULT 0 |
| capacity_excess_qty | BIGINT | 超出数量 | DEFAULT 0 |

### calc_task / import_task / export_task（异步任务）

| 字段 | 类型 | 说明 | 约束 |
|---|---|---|---|
| request_key | VARCHAR(128) | 幂等键 | NOT NULL, UNIQUE |
| status | VARCHAR(32) | PENDING/CLAIMED/RUNNING/SUCCEEDED/FAILED/CANCELLED | NOT NULL, INDEXED |
| worker_id | VARCHAR(64) | 认领的 Worker | NULLABLE |
| lease_until | DATETIME(3) | 租约到期 | NULLABLE |
| heartbeat_at | DATETIME(3) | 最后心跳 | NULLABLE |
| progress_current | INT | 当前进度 | DEFAULT 0 |
| progress_total | INT | 总进度 | DEFAULT 0 |
| attempt_count | INT | 已尝试次数 | DEFAULT 0 |
| max_attempts | INT | 最大尝试次数 | DEFAULT 3 |
| input_checksum | VARCHAR(64) | 输入校验和 | NULLABLE |
| result_resource_id | BIGINT | 结果关联 ID | NULLABLE |
| version | INT | 乐观锁 | DEFAULT 0 |

## 契约对照验证

| 契约要求 | 实现状态 | 证据 |
|---|---|---|
| 主键 BIGINT | ✅ | 全部表 BIGINT AUTO_INCREMENT |
| 数量 BIGINT NOT NULL | ✅ | forecast_qty, quantity, shipped_qty, weekly_capacity, system_quantity 等 |
| 月份 DATE（每月1号） | ✅ | plan_month, physical_month, source_month 均为 DATE |
| 周 week_start_date（周一） | ✅ | plan_detail.week_start_date |
| 料号 VARCHAR 文本 | ✅ | material_id VARCHAR(64) |
| plan_version 绑定全部输入版本 | ✅ | forecast_version_id + inventory_snapshot_id + shipment_batch_id + capacity_version_id + rule_version |
| plan_version 状态机 | ✅ | CALCULATING/READY/PUBLISHED/FAILED |
| plan_override 保存自动值+人工值 | ✅ | system_quantity + manual_quantity |
| 任务状态机 | ✅ | status + request_key UNIQUE + version 乐观锁 |
| audit_log 全字段 | ✅ | operator, action, resource, before/after, trace_id, ip |
| data-contract 索引 | ✅ | 6 个指定索引全部存在 |
| 排产结果禁止 UPDATE | ✅ | 通过版本机制保证（新版本写新行） |
| 软删除只用于配置 | ✅ | is_active 字段仅在 capacity_line |

## 验证命令与结果

```text
# 空库迁移
./mvnw test -B
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

# 表数量
docker exec mrp-mysql mysql -u root -proot -e "USE mrp_test; SHOW TABLES;" | wc -l
30 (20 业务表 + 9 Spring Batch + 1 flyway_schema_history)

# 种子数据
SELECT code FROM role → ADMIN, BUSINESS, PLANNER, READONLY, WAREHOUSE
SELECT username FROM user → admin

# 幂等性（重复测试通过）
第二次 ./mvnw test → BUILD SUCCESS
```

## 门禁结果

| 门禁 | 结果 | 证据 |
|---|---|---|
| 空库迁移成功 | PASS | 30 表创建 |
| 重复执行幂等 | PASS | IF NOT EXISTS + Flyway 版本控制 |
| 索引存在 | PASS | data-contract 6 个索引全部覆盖 |
| 种子数据正确 | PASS | 5 角色 + 1 ADMIN 用户 |

## 未完成项与风险

- 无。Schema 完整覆盖 Phase 1 需求。

## 决策或待确认

- 无新增决策。

## 下一步

- 等待用户确认
- 确认后执行 `.harness/prompts/03-import-and-masterdata.md`
