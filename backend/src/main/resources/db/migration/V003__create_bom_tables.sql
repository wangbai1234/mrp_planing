-- BOM物料父项表（从Oracle同步）
CREATE TABLE IF NOT EXISTS `bom_parent` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `inv_code` VARCHAR(64) NOT NULL COMMENT '父项存货编码',
    `inv_name` VARCHAR(256) COMMENT '父项存货名称',
    `version` VARCHAR(32) COMMENT '版本',
    `parent_qty` BIGINT COMMENT '父项数量',
    `org_name` VARCHAR(128) COMMENT '组织',
    `body_code` VARCHAR(64) COMMENT '库存组织编码',
    `synced_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '同步时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inv_code` (`inv_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- BOM子项明细表（从Oracle同步，取最新版本）
CREATE TABLE IF NOT EXISTS `bom_detail` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `parent_code` VARCHAR(64) NOT NULL COMMENT '父项编码',
    `child_code` VARCHAR(64) NOT NULL COMMENT '子项编码',
    `child_name` VARCHAR(256) COMMENT '子项名称',
    `version` VARCHAR(32) COMMENT '版本',
    `parent_qty` BIGINT COMMENT '父项数量',
    `effective_date` DATE COMMENT '生效日期',
    `material_spec` VARCHAR(256) COMMENT '规格（主料）',
    `make_factory` VARCHAR(256) COMMENT '生产厂家（主料）',
    `child_qty` BIGINT COMMENT '子项数量',
    `child_note` TEXT COMMENT '子项备注',
    `replace_priority` VARCHAR(64) COMMENT '替代优先级',
    `alt_code` VARCHAR(64) COMMENT '替代项存货编码',
    `alt_name` VARCHAR(256) COMMENT '替代项存货名称',
    `alt_spec` VARCHAR(256) COMMENT '规格（替代料）',
    `alt_factory` VARCHAR(256) COMMENT '生产厂家（替代料）',
    `alt_qty` BIGINT COMMENT '子项数量（替代料）',
    `is_deliver` VARCHAR(8) COMMENT '是否发料',
    `is_default` VARCHAR(8) COMMENT '是否默认',
    `main_status` VARCHAR(64) COMMENT '主承认状态',
    `alt_status` VARCHAR(64) COMMENT '替代料承认状态',
    `main_rhos_status` VARCHAR(64) COMMENT '主料RHOS状态',
    `alt_rhos_status` VARCHAR(64) COMMENT '替代料RHOS状态',
    `admit_note` TEXT COMMENT '承认备注',
    `org_name` VARCHAR(128) COMMENT '组织',
    `body_code` VARCHAR(64) COMMENT '库存组织编码',
    `synced_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '同步时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_code` (`parent_code`),
    KEY `idx_child_code` (`child_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- BOM同步状态表
CREATE TABLE IF NOT EXISTS `bom_sync_status` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `sync_type` VARCHAR(32) NOT NULL COMMENT '同步类型：FULL/INCREMENTAL',
    `status` VARCHAR(16) NOT NULL COMMENT '状态：RUNNING/SUCCESS/FAILED',
    `total_rows` BIGINT COMMENT '总行数',
    `synced_rows` BIGINT COMMENT '已同步行数',
    `error_message` TEXT COMMENT '错误信息',
    `started_at` DATETIME(3) NOT NULL COMMENT '开始时间',
    `finished_at` DATETIME(3) COMMENT '结束时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
