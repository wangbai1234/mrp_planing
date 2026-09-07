-- V001: Initial schema for MRP Phase 1
-- All tables use BIGINT primary key, BIGINT for quantities, DATE for months/weeks

-- ========================================
-- User and Role
-- ========================================

CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(64) NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `display_name` VARCHAR(128),
    `is_active` TINYINT(1) NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(32) NOT NULL,
    `name` VARCHAR(64) NOT NULL,
    `description` VARCHAR(255),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_role` (
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    PRIMARY KEY (`user_id`, `role_id`),
    KEY `fk_ur_role` (`role_id`),
    CONSTRAINT `fk_ur_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`),
    CONSTRAINT `fk_ur_role` FOREIGN KEY (`role_id`) REFERENCES `role`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_factory_scope` (
    `user_id` BIGINT NOT NULL,
    `factory_code` VARCHAR(32) NOT NULL,
    PRIMARY KEY (`user_id`, `factory_code`),
    CONSTRAINT `fk_ufs_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Seed data: roles and admin user
-- ========================================

INSERT IGNORE INTO `role` (`code`, `name`, `description`) VALUES
    ('ADMIN', '系统管理员', '全部权限'),
    ('PLANNER', '计划人员', '排产、调整、发布'),
    ('BUSINESS', '商务人员', '经营计划导入'),
    ('WAREHOUSE', '仓库人员', '库存快照导入'),
    ('READONLY', '只读用户', '查看权限');

-- Default admin user: admin / admin123 (BCrypt)
INSERT IGNORE INTO `user` (`username`, `password_hash`, `display_name`) VALUES
    ('admin', '$2b$10$F17Gmp5YjVbFVtsop3Eb6OITyzwSqepFInIulR6ZOFXEX69OeYc22', '系统管理员');

INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id FROM `user` u, `role` r WHERE u.username = 'admin' AND r.code = 'ADMIN';

-- ========================================
-- Forecast (经营计划)
-- ========================================

CREATE TABLE IF NOT EXISTS `forecast_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `version_no` INT NOT NULL,
    `file_name` VARCHAR(255),
    `file_checksum` VARCHAR(64),
    `status` VARCHAR(32) NOT NULL DEFAULT 'IMPORTED',
    `created_by` BIGINT,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_fv_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `forecast_detail` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `version_id` BIGINT NOT NULL,
    `factory_code` VARCHAR(32),
    `material_id` VARCHAR(64) NOT NULL,
    `material_name` VARCHAR(255),
    `business_line` VARCHAR(64),
    `form_type` VARCHAR(64),
    `project` VARCHAR(128),
    `platform` VARCHAR(128),
    `mold` VARCHAR(128),
    `status` VARCHAR(64),
    `plan_month` DATE NOT NULL COMMENT 'Monthly 1st day',
    `forecast_qty` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fd_version_material_month` (`version_id`, `material_id`, `plan_month`),
    KEY `idx_fd_version` (`version_id`),
    CONSTRAINT `fk_fd_version` FOREIGN KEY (`version_id`) REFERENCES `forecast_version`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Inventory (库存快照)
-- ========================================

CREATE TABLE IF NOT EXISTS `inventory_snapshot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `snapshot_date` DATE NOT NULL,
    `file_name` VARCHAR(255),
    `file_checksum` VARCHAR(64),
    `status` VARCHAR(32) NOT NULL DEFAULT 'IMPORTED',
    `created_by` BIGINT,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_is_date` (`snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `inventory_detail` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `snapshot_id` BIGINT NOT NULL,
    `factory_code` VARCHAR(32) NOT NULL,
    `material_id` VARCHAR(64) NOT NULL,
    `warehouse_code` VARCHAR(64) NOT NULL,
    `warehouse_name` VARCHAR(128),
    `warehouse_type` VARCHAR(32),
    `quantity` BIGINT NOT NULL DEFAULT 0,
    `is_in_calculation` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '0=不良品仓不计入',
    PRIMARY KEY (`id`),
    KEY `idx_id_snapshot_factory_material` (`snapshot_id`, `factory_code`, `material_id`),
    CONSTRAINT `fk_id_snapshot` FOREIGN KEY (`snapshot_id`) REFERENCES `inventory_snapshot`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Shipment (CRM 已出货)
-- ========================================

CREATE TABLE IF NOT EXISTS `shipment_batch` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `source` VARCHAR(32) NOT NULL DEFAULT 'CRM_API',
    `status` VARCHAR(32) NOT NULL DEFAULT 'IMPORTED',
    `created_by` BIGINT,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `shipment_detail` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `batch_id` BIGINT NOT NULL,
    `material_id` VARCHAR(64) NOT NULL,
    `plan_month` DATE NOT NULL COMMENT 'Monthly 1st day',
    `shipped_qty` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sd_batch_material_month` (`batch_id`, `material_id`, `plan_month`),
    KEY `idx_sd_batch` (`batch_id`),
    CONSTRAINT `fk_sd_batch` FOREIGN KEY (`batch_id`) REFERENCES `shipment_batch`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Capacity (产能)
-- ========================================

CREATE TABLE IF NOT EXISTS `capacity_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `version_no` INT NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `created_by` BIGINT,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `capacity_line` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `version_id` BIGINT NOT NULL,
    `factory_code` VARCHAR(32) NOT NULL,
    `line_code` VARCHAR(64) NOT NULL,
    `line_name` VARCHAR(128),
    `weekly_capacity` BIGINT NOT NULL COMMENT 'Must be > 0',
    `effective_date` DATE,
    `is_active` TINYINT(1) NOT NULL DEFAULT 1,
    `remark` VARCHAR(512),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cl_version_factory_line` (`version_id`, `factory_code`, `line_code`),
    CONSTRAINT `fk_cl_version` FOREIGN KEY (`version_id`) REFERENCES `capacity_version`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Plan (排产)
-- ========================================

CREATE TABLE IF NOT EXISTS `plan_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `version_no` INT NOT NULL,
    `factory_code` VARCHAR(32) NOT NULL,
    `forecast_version_id` BIGINT NOT NULL,
    `inventory_snapshot_id` BIGINT,
    `shipment_batch_id` BIGINT,
    `capacity_version_id` BIGINT,
    `rule_version` VARCHAR(32) NOT NULL DEFAULT 'v1',
    `current_week_start` DATE NOT NULL COMMENT 'Monday of current week',
    `input_checksum` VARCHAR(64),
    `result_checksum` VARCHAR(64),
    `status` VARCHAR(32) NOT NULL DEFAULT 'CALCULATING' COMMENT 'CALCULATING/READY/PUBLISHED/FAILED',
    `auto_recalc_forecast` TINYINT(1) NOT NULL DEFAULT 0,
    `auto_recalc_inventory` TINYINT(1) NOT NULL DEFAULT 0,
    `created_by` BIGINT,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_pv_status` (`status`),
    KEY `idx_pv_factory` (`factory_code`),
    CONSTRAINT `fk_pv_forecast` FOREIGN KEY (`forecast_version_id`) REFERENCES `forecast_version`(`id`),
    CONSTRAINT `fk_pv_inventory` FOREIGN KEY (`inventory_snapshot_id`) REFERENCES `inventory_snapshot`(`id`),
    CONSTRAINT `fk_pv_shipment` FOREIGN KEY (`shipment_batch_id`) REFERENCES `shipment_batch`(`id`),
    CONSTRAINT `fk_pv_capacity` FOREIGN KEY (`capacity_version_id`) REFERENCES `capacity_version`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `plan_detail` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `plan_version_id` BIGINT NOT NULL,
    `factory_code` VARCHAR(32) NOT NULL,
    `material_id` VARCHAR(64) NOT NULL,
    `material_name` VARCHAR(255),
    `model` VARCHAR(128),
    `me_material_id` VARCHAR(64) COMMENT '机头料号',
    `week_start_date` DATE NOT NULL COMMENT 'Monday',
    `physical_month` DATE NOT NULL COMMENT 'Physical month 1st day',
    `source_month` DATE NOT NULL COMMENT 'Source forecast month 1st day',
    `slot` TINYINT NOT NULL COMMENT '0=carry, 1..3=ordinary',
    `is_carry` TINYINT(1) NOT NULL DEFAULT 0,
    `is_locked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Executed week locked',
    `system_quantity` BIGINT NOT NULL DEFAULT 0,
    `manual_quantity` BIGINT COMMENT 'Nullable, human override',
    `effective_quantity` BIGINT NOT NULL DEFAULT 0,
    `capacity_exceeded` TINYINT(1) NOT NULL DEFAULT 0,
    `capacity_excess_qty` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_pd_version_factory_material_week` (`plan_version_id`, `factory_code`, `material_id`, `week_start_date`),
    KEY `idx_pd_version_week` (`plan_version_id`, `week_start_date`),
    CONSTRAINT `fk_pd_version` FOREIGN KEY (`plan_version_id`) REFERENCES `plan_version`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `plan_override` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `plan_version_id` BIGINT NOT NULL,
    `material_id` VARCHAR(64) NOT NULL,
    `week_start_date` DATE NOT NULL,
    `system_quantity` BIGINT NOT NULL,
    `manual_quantity` BIGINT NOT NULL,
    `reason` VARCHAR(512),
    `operated_by` BIGINT,
    `operated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_po_version_material_week` (`plan_version_id`, `material_id`, `week_start_date`),
    CONSTRAINT `fk_po_version` FOREIGN KEY (`plan_version_id`) REFERENCES `plan_version`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Async Tasks
-- ========================================

CREATE TABLE IF NOT EXISTS `import_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_type` VARCHAR(32) NOT NULL COMMENT 'FORECAST/INVENTORY/SHIPMENT',
    `business_scope` VARCHAR(64),
    `request_key` VARCHAR(128) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `priority` INT NOT NULL DEFAULT 0,
    `worker_id` VARCHAR(64),
    `lease_until` DATETIME(3),
    `heartbeat_at` DATETIME(3),
    `progress_current` INT NOT NULL DEFAULT 0,
    `progress_total` INT NOT NULL DEFAULT 0,
    `attempt_count` INT NOT NULL DEFAULT 0,
    `max_attempts` INT NOT NULL DEFAULT 3,
    `next_run_at` DATETIME(3),
    `input_payload` JSON,
    `input_checksum` VARCHAR(64),
    `result_resource_id` BIGINT,
    `error_code` VARCHAR(64),
    `error_message` TEXT,
    `created_by` BIGINT,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `started_at` DATETIME(3),
    `finished_at` DATETIME(3),
    `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_it_request_key` (`request_key`),
    KEY `idx_it_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `calc_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_type` VARCHAR(32) NOT NULL DEFAULT 'RECALCULATION',
    `business_scope` VARCHAR(64),
    `request_key` VARCHAR(128) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `priority` INT NOT NULL DEFAULT 0,
    `worker_id` VARCHAR(64),
    `lease_until` DATETIME(3),
    `heartbeat_at` DATETIME(3),
    `progress_current` INT NOT NULL DEFAULT 0,
    `progress_total` INT NOT NULL DEFAULT 0,
    `attempt_count` INT NOT NULL DEFAULT 0,
    `max_attempts` INT NOT NULL DEFAULT 3,
    `next_run_at` DATETIME(3),
    `input_payload` JSON,
    `input_checksum` VARCHAR(64),
    `result_resource_id` BIGINT,
    `error_code` VARCHAR(64),
    `error_message` TEXT,
    `created_by` BIGINT,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `started_at` DATETIME(3),
    `finished_at` DATETIME(3),
    `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ct_request_key` (`request_key`),
    KEY `idx_ct_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `export_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_type` VARCHAR(32) NOT NULL DEFAULT 'PLAN_EXPORT',
    `business_scope` VARCHAR(64),
    `request_key` VARCHAR(128) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `priority` INT NOT NULL DEFAULT 0,
    `worker_id` VARCHAR(64),
    `lease_until` DATETIME(3),
    `heartbeat_at` DATETIME(3),
    `progress_current` INT NOT NULL DEFAULT 0,
    `progress_total` INT NOT NULL DEFAULT 0,
    `attempt_count` INT NOT NULL DEFAULT 0,
    `max_attempts` INT NOT NULL DEFAULT 3,
    `next_run_at` DATETIME(3),
    `input_payload` JSON,
    `input_checksum` VARCHAR(64),
    `result_resource_id` BIGINT,
    `error_code` VARCHAR(64),
    `error_message` TEXT,
    `created_by` BIGINT,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `started_at` DATETIME(3),
    `finished_at` DATETIME(3),
    `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_et_request_key` (`request_key`),
    KEY `idx_et_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Audit Log
-- ========================================

CREATE TABLE IF NOT EXISTS `audit_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `operator_id` BIGINT,
    `operator_role` VARCHAR(32),
    `factory_scope` VARCHAR(255),
    `action` VARCHAR(64) NOT NULL,
    `resource_type` VARCHAR(64) NOT NULL,
    `resource_id` VARCHAR(128),
    `before_value` JSON,
    `after_value` JSON,
    `input_version_id` BIGINT,
    `trace_id` VARCHAR(64),
    `ip_address` VARCHAR(64),
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_al_resource` (`resource_type`, `resource_id`),
    KEY `idx_al_operator` (`operator_id`),
    KEY `idx_al_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
