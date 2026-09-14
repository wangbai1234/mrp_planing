-- V007: Add missing roles (物料管理人员, 管理人员) and their permissions

-- ========================================
-- Add missing roles
-- ========================================

INSERT IGNORE INTO `role` (`code`, `name`, `description`, `status`) VALUES
('MATERIAL', '物料管理人员', 'Phase 2: BOM同步、缺料核算权限预留', 'ACTIVE'),
('MANAGER', '管理人员', '查看系统概览、排产和业务数据', 'ACTIVE');

-- ========================================
-- Manager: all view permissions
-- ========================================

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'MANAGER' AND p.code IN (
    'mrp',
    'schedule', 'forecast', 'inventory', 'capacity',
    'schedule:plan', 'schedule:settings', 'forecast:list', 'inventory:list', 'capacity:list',
    'schedule:view', 'schedule:settings:view',
    'forecast:view', 'forecast:version_view',
    'inventory:view', 'inventory:history',
    'capacity:view',
    'system', 'audit:list', 'audit:view'
);

-- ========================================
-- Material: Phase 2 reserved (minimal permissions for now)
-- ========================================

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'MATERIAL' AND p.code IN (
    'mrp', 'inventory', 'inventory:list', 'inventory:view'
);
