-- V014: Grant material_category:view to READONLY role

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p
WHERE r.code = 'READONLY' AND p.code IN ('material_category', 'material_category:list', 'material_category:view');
