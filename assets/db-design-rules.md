# 数据库设计规范

## 1. 基本原则

### 1.1 不使用外键约束
- **禁止使用 FOREIGN KEY 约束**
- 数据完整性由应用层保证
- 使用逻辑关联（应用代码维护关联关系）
- 好处：简化迁移、提高写入性能、便于分库分表

### 1.2 命名规范
- 表名：`snake_case`，复数形式（如 `materials`、`users`）
- 列名：`snake_case`
- 主键：统一使用 `id`，BIGINT AUTO_INCREMENT
- 唯一索引：`uk_{表名}_{列名}`（如 `uk_material_code`）
- 普通索引：`idx_{表名}_{列名}`（如 `idx_material_category`）
- 外键列：`{关联表单数}_id`（如 `user_id`、`version_id`）

### 1.3 通用字段
每个表必须包含以下字段：
```sql
`id` BIGINT NOT NULL AUTO_INCREMENT,
`is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除（0=未删除，1=已删除）',
`created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
`updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
PRIMARY KEY (`id`)
```

### 1.4 逻辑删除规范
- **禁止物理删除**，所有删除操作使用逻辑删除
- 字段名：`is_deleted`，类型：`TINYINT(1) NOT NULL DEFAULT 0`
- 0 = 未删除，1 = 已删除
- 所有查询必须带 `WHERE is_deleted = 0` 条件
- 唯一索引需要包含 `is_deleted` 字段（或使用条件唯一索引）
- 如需恢复，将 `is_deleted` 置为 0

### 1.4 字段类型选择
- 数量/金额：`BIGINT`（整数）或 `DECIMAL(20,4)`（精确小数）
- 禁止使用 `FLOAT`/`DOUBLE` 做业务计算
- 字符串：`VARCHAR(N)`，N 为 2 的幂次（32, 64, 128, 256, 512）
- 布尔：`TINYINT(1) NOT NULL DEFAULT 0`
- 日期：`DATE`（仅日期）或 `DATETIME(3)`（含时间）
- JSON：`JSON` 类型（MySQL 5.7+）

### 1.5 索引策略
- 主键索引：自动创建
- 唯一索引：业务唯一约束
- 普通索引：高频查询字段
- 组合索引：遵循最左前缀原则
- 单表索引数量不超过 5 个

## 2. 表设计模板

```sql
CREATE TABLE IF NOT EXISTS `table_name` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `business_key` VARCHAR(64) NOT NULL COMMENT '业务唯一键',
    `name` VARCHAR(128) NOT NULL COMMENT '名称',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否有效',
    `created_by` BIGINT COMMENT '创建人ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_table_business_key` (`business_key`),
    KEY `idx_table_status` (`status`),
    KEY `idx_table_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## 3. 关联关系处理

### 3.1 一对多关系
在"多"方添加外键列，不创建外键约束：
```sql
-- 订单表
CREATE TABLE `orders` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID，逻辑关联user表',
    -- 其他字段
    KEY `idx_order_user` (`user_id`)
);
```

### 3.2 多对多关系
使用中间表，不创建外键约束：
```sql
-- 用户角色中间表
CREATE TABLE `user_role` (
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    PRIMARY KEY (`user_id`, `role_id`),
    KEY `idx_ur_role` (`role_id`)
);
```

## 4. 物料表设计示例

根据业务需求，物料表字段映射：
- 料号 → `material_code` (VARCHAR(64), UNIQUE)
- 物料名称 → `material_name` (VARCHAR(256))
- 项目型号 → `project_model` (VARCHAR(128))
- 规格型号 → `spec_model` (VARCHAR(256))
- 单位 → `unit` (VARCHAR(32))
- MOQ最小起订量 → `moq` (BIGINT)
- MPQ最小包装 → `mpq` (BIGINT)
- 所属区域 → `region` (VARCHAR(64))
- 属性 → `attribute` (VARCHAR(64))
- 物料类别 → `category` (VARCHAR(64))
- 是否有效 → `is_active` (TINYINT(1))
- L-T提前期 → `lead_time_days` (INT)
- 产地 → `origin_place` (VARCHAR(128))

## 5. 迁移文件命名

- 格式：`V{序号}__{描述}.sql`
- 示例：`V002__create_material_table.sql`
- 一旦应用，禁止修改已有的迁移文件

---
**最后更新**: 2026-09-04
**版本**: 1.0.0
