-- V010: 物料分类表（支持多级树状结构）

CREATE TABLE IF NOT EXISTS `material_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(32) NOT NULL COMMENT '分类编码，业务唯一键',
    `name` VARCHAR(128) NOT NULL COMMENT '分类名称',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父分类ID，NULL表示一级分类',
    `level` INT NOT NULL DEFAULT 1 COMMENT '分类层级（1=一级，2=二级...）',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用（1=是，0=否）',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除（0=未删除，1=已删除）',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_code` (`code`, `is_deleted`),
    KEY `idx_category_parent` (`parent_id`),
    KEY `idx_category_level` (`level`),
    KEY `idx_category_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料分类表';
