# MRP 数据与版本契约

## 1. 公共字段规范

- 主键：`BIGINT` 自增或雪花/UUID 转字符串只能二选一；本项目默认 `BIGINT`；
- 数量：`BIGINT NOT NULL`，应用层禁止负值；
- 月份：MySQL `DATE`，统一存每月 1 号，Java 使用 `YearMonth`；
- 周：`week_start_date DATE`，必须是周一；展示范围为周一至周日；
- 时间：数据库存 UTC `DATETIME(3)`，接口使用 ISO-8601，前端按 Asia/Shanghai 展示；
- 料号、仓库编码、产线编码：`VARCHAR`，导入和前端均按文本；
- 软删除只用于配置资料；版本输入和排产结果禁止物理覆盖。

## 2. 最小实体集合

| 实体 | 作用 | 关键不变量 |
|---|---|---|
| `user` / `role` / `user_factory_scope` | 用户权限 | 用户只能访问授权工厂 |
| `forecast_version` / `forecast_detail` | 经营计划版本 | 成功导入后不可修改；料号+月份唯一 |
| `inventory_snapshot` / `inventory_detail` | 库存快照 | 快照日期和来源文件可追溯；不良品仓可识别 |
| `shipment_batch` / `shipment_detail` | CRM 已出货批次 | 整机料号+月份累计；同步批次不可覆盖 |
| `capacity_version` / `capacity_line` | 周产能版本 | 工厂内产线编码唯一；周产能>0 |
| `plan_version` / `plan_detail` | 排产版本及周结果 | 绑定全部输入版本和规则版本 |
| `plan_override` | 人工覆盖 | 保存自动值、人工值、原因、操作人和时间 |
| `import_task` / `calc_task` | 异步任务 | 状态机不可逆乱跳；失败可追溯 |
| `audit_log` | 操作审计 | 写操作必须记录资源、前后值和操作者 |
| `schema_migration` | 自定义 SQL 迁移记录 | 每个版本只执行一次 |

## 3. 版本绑定

`plan_version` 必须保存：

```text
forecast_version_id
inventory_snapshot_id
shipment_batch_id
capacity_version_id
rule_version
current_week_start
input_checksum
result_checksum
status
```

排产版本状态：`CALCULATING -> READY -> PUBLISHED`。失败只能进入 `FAILED`；`READY` 或 `PUBLISHED` 不允许回写计算结果。发布后人工修改进入 `plan_override` 和审计日志，不改变版本号。

## 4. 事务要求

- 导入确认：创建正式版本、明细和审计记录必须同一事务；
- 重算完成：结果批量落库后，在同一事务中写 checksum 并把版本改为 `READY`；
- 发布：检查阻断项、更新状态和写审计同一事务；
- 人工调整：写 override、更新当前值视图/查询结果和审计同一事务；
- 任何跨表写入失败都回滚，不能留下“半版本”。

## 5. 索引和批量

至少建立：

```text
forecast_detail(version_id, material_id, plan_month)
inventory_detail(snapshot_id, factory_code, material_id)
shipment_detail(batch_id, material_id, plan_month)
plan_detail(plan_version_id, factory_code, material_id, week_start_date)
plan_override(plan_version_id, material_id, week_start_date)
calc_task(status, created_at)
```

MyBatis 批量写入每批 500–2000 行；禁止逐行查询形成 N+1。结果查询必须支持按工厂、料号、状态和 12 周窗口分页/虚拟化。
