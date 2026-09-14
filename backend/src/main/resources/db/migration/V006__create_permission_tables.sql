-- V006: Permission system tables for RBAC + button-level permissions
-- Extends user/role tables and adds permission, role_permission, user_permission, role_data_scope

-- ========================================
-- Extend role table
-- ========================================

ALTER TABLE `role` ADD COLUMN `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' AFTER `description`;
ALTER TABLE `role` ADD COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER `status`;
ALTER TABLE `role` ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER `created_at`;

-- ========================================
-- Extend user table
-- ========================================

ALTER TABLE `user` ADD COLUMN `department` VARCHAR(128) AFTER `display_name`;
ALTER TABLE `user` ADD COLUMN `email` VARCHAR(128) AFTER `department`;
ALTER TABLE `user` ADD COLUMN `phone` VARCHAR(32) AFTER `email`;
ALTER TABLE `user` ADD COLUMN `last_login_at` DATETIME(3) AFTER `is_active`;

-- ========================================
-- Permission table
-- ========================================

CREATE TABLE IF NOT EXISTS `permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(128) NOT NULL COMMENT 'Permission code like schedule:view',
    `name` VARCHAR(128) NOT NULL COMMENT 'Display name',
    `type` VARCHAR(16) NOT NULL COMMENT 'MENU/PAGE/BUTTON/DATA',
    `parent_id` BIGINT DEFAULT NULL COMMENT 'Parent permission id for tree structure',
    `resource` VARCHAR(128) DEFAULT NULL COMMENT 'Resource identifier',
    `action` VARCHAR(64) DEFAULT NULL COMMENT 'Action type',
    `sort` INT NOT NULL DEFAULT 0 COMMENT 'Display order',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    `description` VARCHAR(512) DEFAULT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_code` (`code`),
    KEY `idx_permission_parent` (`parent_id`),
    KEY `idx_permission_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Role-Permission association
-- ========================================

CREATE TABLE IF NOT EXISTS `role_permission` (
    `role_id` BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`role_id`, `permission_id`),
    KEY `fk_rp_permission` (`permission_id`),
    CONSTRAINT `fk_rp_role` FOREIGN KEY (`role_id`) REFERENCES `role`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_rp_permission` FOREIGN KEY (`permission_id`) REFERENCES `permission`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- User-Permission override (ALLOW/DENY)
-- ========================================

CREATE TABLE IF NOT EXISTS `user_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    `effect` VARCHAR(8) NOT NULL COMMENT 'ALLOW or DENY',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_by` BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_permission` (`user_id`, `permission_id`),
    KEY `fk_up_permission` (`permission_id`),
    CONSTRAINT `fk_up_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_up_permission` FOREIGN KEY (`permission_id`) REFERENCES `permission`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Role Data Scope
-- ========================================

CREATE TABLE IF NOT EXISTS `role_data_scope` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `role_id` BIGINT NOT NULL,
    `scope_type` VARCHAR(32) NOT NULL COMMENT 'ALL/FACTORY/WAREHOUSE/LINE/SELF',
    `scope_value` VARCHAR(64) DEFAULT NULL COMMENT 'Factory code, warehouse code, etc.',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_rds_role` (`role_id`),
    CONSTRAINT `fk_rds_role` FOREIGN KEY (`role_id`) REFERENCES `role`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Seed permission data
-- ========================================

-- Level 1: Root
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('mrp', 'MRP系统', 'MENU', NULL, NULL, NULL, 0);

-- Level 2: Modules
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('schedule', '排产管理', 'MENU', 1, 'schedule', NULL, 1),
('forecast', '经营计划', 'MENU', 1, 'forecast', NULL, 2),
('inventory', '库存管理', 'MENU', 1, 'inventory', NULL, 3),
('capacity', '产能配置', 'MENU', 1, 'capacity', NULL, 4),
('system', '系统管理', 'MENU', 1, 'system', NULL, 10);

-- Level 3: Pages under schedule
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('schedule:plan', '排产计划', 'PAGE', 2, 'schedule', 'plan', 1),
('schedule:settings', '数据与重算设置', 'PAGE', 2, 'schedule', 'settings', 2);

-- Level 3: Pages under forecast
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('forecast:list', '经营计划列表', 'PAGE', 3, 'forecast', 'list', 1);

-- Level 3: Pages under inventory
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('inventory:list', '库存列表', 'PAGE', 4, 'inventory', 'list', 1);

-- Level 3: Pages under capacity
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('capacity:list', '产能列表', 'PAGE', 5, 'capacity', 'list', 1);

-- Level 3: Pages under system
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('user:list', '用户管理', 'PAGE', 6, 'user', 'list', 1),
('role:list', '角色管理', 'PAGE', 6, 'role', 'list', 2),
('permission:list', '权限管理', 'PAGE', 6, 'permission', 'list', 3),
('audit:list', '操作日志', 'PAGE', 6, 'audit', 'list', 4);

-- Level 4: Buttons under schedule:plan
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('schedule:view', '查看排产', 'BUTTON', 7, 'schedule', 'view', 1),
('schedule:edit', '编辑排产', 'BUTTON', 7, 'schedule', 'edit', 2),
('schedule:adjust', '人工调整', 'BUTTON', 7, 'schedule', 'adjust', 3),
('schedule:recalculate', '重新计算', 'BUTTON', 7, 'schedule', 'recalculate', 4),
('schedule:publish', '发布排产', 'BUTTON', 7, 'schedule', 'publish', 5),
('schedule:update_published', '发布后修改', 'BUTTON', 7, 'schedule', 'update_published', 6),
('schedule:history', '修改历史', 'BUTTON', 7, 'schedule', 'history', 7),
('schedule:version_compare', '版本对比', 'BUTTON', 7, 'schedule', 'version_compare', 8),
('schedule:export', '导出排产', 'BUTTON', 7, 'schedule', 'export', 9);

-- Level 4: Buttons under schedule:settings
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('schedule:settings:view', '查看设置', 'BUTTON', 8, 'schedule', 'settings_view', 1),
('schedule:auto_recalculate:update', '修改自动重算', 'BUTTON', 8, 'schedule', 'auto_recalc_update', 2);

-- Level 4: Buttons under forecast
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('forecast:view', '查看经营计划', 'BUTTON', 9, 'forecast', 'view', 1),
('forecast:template_download', '下载模板', 'BUTTON', 9, 'forecast', 'template_download', 2),
('forecast:upload', '上传文件', 'BUTTON', 9, 'forecast', 'upload', 3),
('forecast:import', '导入数据', 'BUTTON', 9, 'forecast', 'import', 4),
('forecast:import_confirm', '确认导入', 'BUTTON', 9, 'forecast', 'import_confirm', 5),
('forecast:version_view', '查看历史版本', 'BUTTON', 9, 'forecast', 'version_view', 6);

-- Level 4: Buttons under inventory
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('inventory:view', '查看库存', 'BUTTON', 10, 'inventory', 'view', 1),
('inventory:template_download', '下载模板', 'BUTTON', 10, 'inventory', 'template_download', 2),
('inventory:upload', '上传文件', 'BUTTON', 10, 'inventory', 'upload', 3),
('inventory:import', '导入数据', 'BUTTON', 10, 'inventory', 'import', 4),
('inventory:import_confirm', '确认导入', 'BUTTON', 10, 'inventory', 'import_confirm', 5),
('inventory:history', '历史快照', 'BUTTON', 10, 'inventory', 'history', 6);

-- Level 4: Buttons under capacity
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('capacity:view', '查看产能', 'BUTTON', 11, 'capacity', 'view', 1),
('capacity:create', '新增产能', 'BUTTON', 11, 'capacity', 'create', 2),
('capacity:update', '编辑产能', 'BUTTON', 11, 'capacity', 'update', 3),
('capacity:disable', '停用产能', 'BUTTON', 11, 'capacity', 'disable', 4),
('capacity:delete', '删除产能', 'BUTTON', 11, 'capacity', 'delete', 5);

-- Level 4: Buttons under user management
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('user:view', '查看用户', 'BUTTON', 12, 'user', 'view', 1),
('user:create', '新增用户', 'BUTTON', 12, 'user', 'create', 2),
('user:update', '编辑用户', 'BUTTON', 12, 'user', 'update', 3),
('user:delete', '删除用户', 'BUTTON', 12, 'user', 'delete', 4),
('user:enable', '启用用户', 'BUTTON', 12, 'user', 'enable', 5),
('user:disable', '禁用用户', 'BUTTON', 12, 'user', 'disable', 6),
('user:assign_role', '分配角色', 'BUTTON', 12, 'user', 'assign_role', 7),
('user:view_permissions', '查看权限', 'BUTTON', 12, 'user', 'view_permissions', 8);

-- Level 4: Buttons under role management
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('role:view', '查看角色', 'BUTTON', 13, 'role', 'view', 1),
('role:create', '新增角色', 'BUTTON', 13, 'role', 'create', 2),
('role:update', '编辑角色', 'BUTTON', 13, 'role', 'update', 3),
('role:delete', '删除角色', 'BUTTON', 13, 'role', 'delete', 4),
('role:assign_permission', '配置权限', 'BUTTON', 13, 'role', 'assign_permission', 5),
('role:view_members', '查看成员', 'BUTTON', 13, 'role', 'view_members', 6);

-- Level 4: Buttons under permission management
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('permission:view', '查看权限', 'BUTTON', 14, 'permission', 'view', 1),
('permission:update', '修改权限', 'BUTTON', 14, 'permission', 'update', 2);

-- Level 4: Buttons under audit log
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('audit:view', '查看日志', 'BUTTON', 15, 'audit', 'view', 1),
('audit:export', '导出日志', 'BUTTON', 15, 'audit', 'export', 2);

-- ========================================
-- Seed role permissions
-- ========================================

-- System Admin: all permissions
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p WHERE r.code = 'ADMIN';

-- Business User: forecast permissions
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'BUSINESS' AND p.code IN (
    'mrp', 'forecast', 'forecast:list',
    'forecast:view', 'forecast:template_download', 'forecast:upload',
    'forecast:import', 'forecast:import_confirm', 'forecast:version_view'
);

-- Planner: schedule + capacity permissions
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'PLANNER' AND p.code IN (
    'mrp', 'schedule', 'capacity',
    'schedule:plan', 'schedule:settings',
    'schedule:view', 'schedule:edit', 'schedule:adjust', 'schedule:recalculate',
    'schedule:publish', 'schedule:update_published', 'schedule:history',
    'schedule:version_compare', 'schedule:export',
    'schedule:settings:view', 'schedule:auto_recalculate:update',
    'capacity:view', 'capacity:create', 'capacity:update',
    'capacity:list'
);

-- Warehouse: inventory permissions
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'WAREHOUSE' AND p.code IN (
    'mrp', 'inventory', 'inventory:list',
    'inventory:view', 'inventory:template_download', 'inventory:upload',
    'inventory:import', 'inventory:import_confirm', 'inventory:history'
);

-- Read-only: all view permissions
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'READONLY' AND p.code IN (
    'mrp', 'schedule', 'forecast', 'inventory', 'capacity',
    'schedule:plan', 'schedule:settings', 'forecast:list', 'inventory:list', 'capacity:list',
    'schedule:view', 'schedule:settings:view',
    'forecast:view', 'forecast:version_view',
    'inventory:view', 'inventory:history',
    'capacity:view'
);

-- ========================================
-- Seed role data scope (default: ALL for admin, others TBD)
-- ========================================

INSERT INTO `role_data_scope` (`role_id`, `scope_type`)
SELECT r.id, 'ALL' FROM `role` r WHERE r.code = 'ADMIN';
