# Prompt 02：设计 MySQL 数据模型和迁移

前置：后端骨架可编译。

读取 `data-contract.md`、`calculation-contract.md`、`task-execution-contract.md`、`code-standards.md`、需求和规则库。使用 Flyway 创建版本化 SQL 迁移，不得新增独立迁移服务：

- 用户、角色、工厂数据域；
- 经营计划版本/明细；
- 库存快照/明细；
- CRM 出货批次/明细；
- 周产能版本/产线；
- 排产版本/周结果；
- 人工覆盖、导入任务、计算任务、审计日志；
- 必要唯一键、外键、检查约束和索引。

重点：月份用 DATE 存每月 1 号，周用周一 DATE，料号为 VARCHAR，数量为 BIGINT；排产版本必须绑定全部输入版本和 rule_version。迁移必须可重复执行，空库启动成功，不能破坏已有数据。输出 ER 说明、字段字典、迁移测试和任务报告。完成后状态设为 REVIEW，等待复核。
