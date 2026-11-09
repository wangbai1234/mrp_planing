-- V030: Complete Phase 2 page and action permissions.
-- V025 only added batch/decision/plan permissions, leaving configuration,
-- calendar, inventory allocation and execution actions absent from role setup.

SET @mrp_root = (SELECT id FROM permission WHERE code = 'mrp' LIMIT 1);

INSERT IGNORE INTO permission (code, name, type, parent_id, resource, action, sort) VALUES
('mrp_workspace', 'MRP工作台', 'MENU', @mrp_root, 'mrp_workspace', NULL, 7),
('mrp_config', 'MRP计算配置', 'MENU', @mrp_root, 'mrp_config', NULL, 8),
('mrp_inventory', 'MRP库存分配', 'MENU', @mrp_root, 'mrp_inventory', NULL, 9);

SET @workspace_menu = (SELECT id FROM permission WHERE code = 'mrp_workspace' LIMIT 1);
SET @config_menu = (SELECT id FROM permission WHERE code = 'mrp_config' LIMIT 1);
SET @inventory_menu = (SELECT id FROM permission WHERE code = 'mrp_inventory' LIMIT 1);
SET @plan_page = (SELECT id FROM permission WHERE code = 'mrp_plan:list' LIMIT 1);
SET @batch_menu = (SELECT id FROM permission WHERE code = 'mrp_batch' LIMIT 1);

INSERT IGNORE INTO permission (code, name, type, parent_id, resource, action, sort) VALUES
('mrp_workspace:view', '查看MRP工作台', 'PAGE', @workspace_menu, 'mrp_workspace', 'view', 1),
('mrp_config:material_policy', '物料策略', 'PAGE', @config_menu, 'mrp_config', 'material_policy', 1),
('mrp_calendar:view', '查看生产日历', 'PAGE', @config_menu, 'mrp_calendar', 'view', 2),
('mrp_warehouse_mapping:view', '查看工厂仓库映射', 'PAGE', @config_menu, 'mrp_warehouse_mapping', 'view', 3),
('mrp_stock_policy:view', '查看库存控制策略', 'PAGE', @config_menu, 'mrp_stock_policy', 'view', 4),
('mrp_business_week:view', '查看业务周期间', 'PAGE', @config_menu, 'mrp_business_week', 'view', 5),
('mrp_inventory:view', '查看库存分配', 'PAGE', @inventory_menu, 'mrp_inventory', 'view', 1),
('mrp_history:view', '查看历史排产版本', 'PAGE', @batch_menu, 'mrp_history', 'view', 9);

SET @material_policy_page = (SELECT id FROM permission WHERE code = 'mrp_config:material_policy' LIMIT 1);
SET @calendar_page = (SELECT id FROM permission WHERE code = 'mrp_calendar:view' LIMIT 1);
SET @warehouse_mapping_page = (SELECT id FROM permission WHERE code = 'mrp_warehouse_mapping:view' LIMIT 1);
SET @stock_policy_page = (SELECT id FROM permission WHERE code = 'mrp_stock_policy:view' LIMIT 1);
SET @business_week_page = (SELECT id FROM permission WHERE code = 'mrp_business_week:view' LIMIT 1);
SET @inventory_page = (SELECT id FROM permission WHERE code = 'mrp_inventory:view' LIMIT 1);

INSERT IGNORE INTO permission (code, name, type, parent_id, resource, action, sort) VALUES
('mrp_config:view', '查看物料策略', 'BUTTON', @material_policy_page, 'mrp_config', 'view', 1),
('mrp_config:manage', '维护物料策略', 'BUTTON', @material_policy_page, 'mrp_config', 'manage', 2),
('mrp_calendar:manage', '维护生产日历', 'BUTTON', @calendar_page, 'mrp_calendar', 'manage', 1),
('mrp_warehouse_mapping:manage', '维护工厂仓库映射', 'BUTTON', @warehouse_mapping_page, 'mrp_warehouse_mapping', 'manage', 1),
('mrp_stock_policy:manage', '维护库存控制策略', 'BUTTON', @stock_policy_page, 'mrp_stock_policy', 'manage', 1),
('mrp_business_week:manage', '维护业务周期间', 'BUTTON', @business_week_page, 'mrp_business_week', 'manage', 1),
('mrp_inventory:allocate', '调整库存分配', 'BUTTON', @inventory_page, 'mrp_inventory', 'allocate', 1),
('mrp_execution:confirm', '确认执行与锁定', 'BUTTON', @plan_page, 'mrp_execution', 'confirm', 3);

-- Administrators receive all new permissions.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'mrp_workspace', 'mrp_workspace:view', 'mrp_config',
    'mrp_config:material_policy', 'mrp_config:view', 'mrp_config:manage',
    'mrp_calendar:view', 'mrp_calendar:manage',
    'mrp_warehouse_mapping:view', 'mrp_warehouse_mapping:manage',
    'mrp_stock_policy:view', 'mrp_stock_policy:manage',
    'mrp_business_week:view', 'mrp_business_week:manage',
    'mrp_inventory', 'mrp_inventory:view', 'mrp_inventory:allocate',
    'mrp_execution:confirm', 'mrp_history:view'
)
WHERE r.code = 'ADMIN';

-- Planners operate the full Phase 2 workflow.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'mrp_workspace', 'mrp_workspace:view', 'mrp_config',
    'mrp_config:material_policy', 'mrp_config:view', 'mrp_config:manage',
    'mrp_calendar:view', 'mrp_calendar:manage',
    'mrp_warehouse_mapping:view', 'mrp_warehouse_mapping:manage',
    'mrp_stock_policy:view', 'mrp_stock_policy:manage',
    'mrp_business_week:view', 'mrp_business_week:manage',
    'mrp_inventory', 'mrp_inventory:view', 'mrp_inventory:allocate',
    'mrp_execution:confirm', 'mrp_history:view'
)
WHERE r.code = 'PLANNER';

-- Warehouse users can see the workbench and maintain inventory allocation.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'mrp_workspace', 'mrp_workspace:view',
    'mrp_inventory', 'mrp_inventory:view', 'mrp_inventory:allocate',
    'mrp_stock_policy:view'
)
WHERE r.code = 'WAREHOUSE';

-- Material users maintain material policy and can read the relevant configuration.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'mrp_workspace', 'mrp_workspace:view', 'mrp_config',
    'mrp_config:material_policy', 'mrp_config:view', 'mrp_config:manage',
    'mrp_calendar:view', 'mrp_warehouse_mapping:view',
    'mrp_stock_policy:view', 'mrp_business_week:view'
)
WHERE r.code = 'MATERIAL';

-- Management and read-only roles only receive query permissions.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'mrp_workspace', 'mrp_workspace:view', 'mrp_config',
    'mrp_config:material_policy', 'mrp_config:view',
    'mrp_calendar:view', 'mrp_warehouse_mapping:view',
    'mrp_stock_policy:view', 'mrp_business_week:view',
    'mrp_inventory', 'mrp_inventory:view', 'mrp_history:view'
)
WHERE r.code IN ('MANAGER', 'READONLY');
