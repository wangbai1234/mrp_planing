-- V013: Add material-category and bom:explode permissions

-- Level 2: Material Category module
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('material_category', '物料分类', 'MENU', 1, 'material_category', NULL, 7);

-- Level 3: Page under material_category
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('material_category:list', '物料分类列表', 'PAGE', (SELECT id FROM (SELECT id FROM permission WHERE code='material_category') t), 'material_category', 'list', 1);

-- Level 4: Buttons under material_category
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('material_category:view', '查看物料分类', 'BUTTON', (SELECT id FROM (SELECT id FROM permission WHERE code='material_category:list') t), 'material_category', 'view', 1),
('material_category:manage', '管理物料分类', 'BUTTON', (SELECT id FROM (SELECT id FROM permission WHERE code='material_category:list') t), 'material_category', 'manage', 2);

-- Level 4: BOM explosion button under bom
INSERT INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('bom:explode', 'BOM展开', 'BUTTON', (SELECT id FROM (SELECT id FROM permission WHERE code='bom:list') t), 'bom', 'explode', 3);

-- Assign to ADMIN: all new permissions
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'ADMIN' AND p.code IN ('material_category', 'material_category:list', 'material_category:view', 'material_category:manage', 'bom:explode');

-- Assign to PLANNER: view material_category + bom:explode
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'PLANNER' AND p.code IN ('material_category', 'material_category:list', 'material_category:view', 'bom:explode');

-- Assign to READONLY: view material_category
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'READONLY' AND p.code IN ('material_category', 'material_category:list', 'material_category:view');
