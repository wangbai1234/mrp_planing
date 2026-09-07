-- V002: 物料主数据表
-- 无外键约束，数据完整性由应用层保证

CREATE TABLE IF NOT EXISTS `material` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `material_code` VARCHAR(64) NOT NULL COMMENT '料号，业务唯一键',
    `material_name` VARCHAR(256) NOT NULL COMMENT '物料名称',
    `project_model` VARCHAR(128) COMMENT '项目型号',
    `spec_model` VARCHAR(256) COMMENT '规格型号',
    `unit` VARCHAR(32) COMMENT '单位',
    `moq` BIGINT COMMENT 'MOQ最小起订量',
    `mpq` BIGINT COMMENT 'MPQ最小包装',
    `region` VARCHAR(64) COMMENT '所属区域（上海、惠州等）',
    `attribute` VARCHAR(64) COMMENT '属性（限制使用、差异、通用等）',
    `category` VARCHAR(64) COMMENT '物料类别（原材料、整机等）',
    `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否有效（1=是，0=否）',
    `lead_time_days` INT COMMENT 'L-T提前期（天）',
    `origin_place` VARCHAR(128) COMMENT '产地',
    `data_source` VARCHAR(32) NOT NULL DEFAULT 'EXCEL_IMPORT' COMMENT '数据来源（EXCEL_IMPORT/API_SYNC）',
    `external_id` VARCHAR(128) COMMENT '外部系统ID（用于同步）',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除（0=未删除，1=已删除）',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_material_code` (`material_code`, `is_deleted`),
    KEY `idx_material_category` (`category`),
    KEY `idx_material_region` (`region`),
    KEY `idx_material_is_active` (`is_active`),
    KEY `idx_material_external_id` (`external_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料主数据表';
