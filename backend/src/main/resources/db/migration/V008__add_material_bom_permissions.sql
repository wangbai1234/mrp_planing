-- V008: Add material and BOM permissions for SecurityConfig

-- Level 2: Material and BOM modules
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('material', '物料管理', 'MENU', 1, 'material', NULL, 5),
('bom', 'BOM管理', 'MENU', 1, 'bom', NULL, 6);

-- Level 3: Pages under material and bom
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('material:list', '物料列表', 'PAGE', (SELECT id FROM (SELECT id FROM permission WHERE code='material') t), 'material', 'list', 1),
('bom:list', 'BOM列表', 'PAGE', (SELECT id FROM (SELECT id FROM permission WHERE code='bom') t), 'bom', 'list', 1);

-- Level 4: Buttons under material
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('material:view', '查看物料', 'BUTTON', (SELECT id FROM (SELECT id FROM permission WHERE code='material:list') t), 'material', 'view', 1),
('material:manage', '管理物料', 'BUTTON', (SELECT id FROM (SELECT id FROM permission WHERE code='material:list') t), 'material', 'manage', 2);

-- Level 4: Buttons under bom
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('bom:view', '查看BOM', 'BUTTON', (SELECT id FROM (SELECT id FROM permission WHERE code='bom:list') t), 'bom', 'view', 1),
('bom:manage', '管理BOM', 'BUTTON', (SELECT id FROM (SELECT id FROM permission WHERE code='bom:list') t), 'bom', 'manage', 2);

-- Grant material:view to ADMIN, PLANNER, READONLY
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'ADMIN' AND p.code IN ('material', 'material:list', 'material:view', 'material:manage', 'bom', 'bom:list', 'bom:view', 'bom:manage');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'PLANNER' AND p.code IN ('material', 'material:list', 'material:view', 'bom', 'bom:list', 'bom:view');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'READONLY' AND p.code IN ('material', 'material:list', 'material:view', 'bom', 'bom:list', 'bom:view');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'MATERIAL' AND p.code IN ('material', 'material:list', 'material:view', 'material:manage', 'bom', 'bom:list', 'bom:view');
