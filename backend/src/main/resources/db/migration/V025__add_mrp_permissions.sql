-- V025: Add MRP Phase 2 permissions
-- Adds mrp_batch, mrp_decision, mrp_plan permission groups

-- Level 2: MRP module (if not already exists)
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('mrp_batch', 'MRP批次管理', 'MENU', 1, 'mrp_batch', NULL, 5);

-- Get the parent id for mrp_batch module
SET @mrp_batch_parent = (SELECT id FROM `permission` WHERE code = 'mrp_batch' LIMIT 1);

-- Level 3: MRP batch pages
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('mrp_batch:list', '批次列表', 'PAGE', @mrp_batch_parent, 'mrp_batch', 'list', 1);

SET @mrp_batch_page = (SELECT id FROM `permission` WHERE code = 'mrp_batch:list' LIMIT 1);

-- Level 4: MRP batch buttons
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('mrp_batch:create', '创建批次', 'BUTTON', @mrp_batch_page, 'mrp_batch', 'create', 1),
('mrp_batch:precheck', '预检批次', 'BUTTON', @mrp_batch_page, 'mrp_batch', 'precheck', 2),
('mrp_batch:calculate', '计算批次', 'BUTTON', @mrp_batch_page, 'mrp_batch', 'calculate', 3),
('mrp_batch:publish', '发布批次', 'BUTTON', @mrp_batch_page, 'mrp_batch', 'publish', 4);

-- Level 3: MRP decision page
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('mrp_decision', 'MRP决策管理', 'MENU', 1, 'mrp_decision', NULL, 6);

SET @mrp_decision_parent = (SELECT id FROM `permission` WHERE code = 'mrp_decision' LIMIT 1);

INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('mrp_decision:list', '决策列表', 'PAGE', @mrp_decision_parent, 'mrp_decision', 'list', 1);

SET @mrp_decision_page = (SELECT id FROM `permission` WHERE code = 'mrp_decision:list' LIMIT 1);

INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('mrp_decision:manage', '管理决策', 'BUTTON', @mrp_decision_page, 'mrp_decision', 'manage', 1);

-- Level 3: MRP plan adjustment page
INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('mrp_plan', 'MRP排产调整', 'MENU', 1, 'mrp_plan', NULL, 7);

SET @mrp_plan_parent = (SELECT id FROM `permission` WHERE code = 'mrp_plan' LIMIT 1);

INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('mrp_plan:list', '排产列表', 'PAGE', @mrp_plan_parent, 'mrp_plan', 'list', 1);

SET @mrp_plan_page = (SELECT id FROM `permission` WHERE code = 'mrp_plan:list' LIMIT 1);

INSERT IGNORE INTO `permission` (`code`, `name`, `type`, `parent_id`, `resource`, `action`, `sort`) VALUES
('mrp_plan:adjust', '调整排产', 'BUTTON', @mrp_plan_page, 'mrp_plan', 'adjust', 1),
('mrp_plan:export', '导出排产', 'BUTTON', @mrp_plan_page, 'mrp_plan', 'export', 2);

-- Grant MRP permissions to ADMIN role
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'ADMIN' AND p.code IN (
    'mrp_batch', 'mrp_batch:list', 'mrp_batch:create', 'mrp_batch:precheck',
    'mrp_batch:calculate', 'mrp_batch:publish',
    'mrp_decision', 'mrp_decision:list', 'mrp_decision:manage',
    'mrp_plan', 'mrp_plan:list', 'mrp_plan:adjust', 'mrp_plan:export'
);

-- Grant MRP permissions to PLANNER role
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'PLANNER' AND p.code IN (
    'mrp_batch', 'mrp_batch:list', 'mrp_batch:create', 'mrp_batch:precheck',
    'mrp_batch:calculate', 'mrp_batch:publish',
    'mrp_decision', 'mrp_decision:list', 'mrp_decision:manage',
    'mrp_plan', 'mrp_plan:list', 'mrp_plan:adjust', 'mrp_plan:export'
);

-- Grant read-only MRP permissions to READONLY role
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'READONLY' AND p.code IN (
    'mrp_batch', 'mrp_batch:list',
    'mrp_decision', 'mrp_decision:list',
    'mrp_plan', 'mrp_plan:list'
);
