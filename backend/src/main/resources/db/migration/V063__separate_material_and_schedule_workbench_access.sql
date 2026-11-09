-- The MATERIAL role owns shortage analysis; planner confirmation and machine facts stay with planning.
DELETE rp FROM role_permission rp
JOIN role r ON r.id = rp.role_id
JOIN permission p ON p.id = rp.permission_id
WHERE r.code = 'MATERIAL' AND p.code IN ('mrp_workspace', 'mrp_workspace:view');

DELETE rp FROM role_permission rp
JOIN role r ON r.id = rp.role_id
JOIN permission p ON p.id = rp.permission_id
WHERE r.code = 'PLANNER' AND p.code IN ('mrp_shortage', 'mrp_shortage:view');
