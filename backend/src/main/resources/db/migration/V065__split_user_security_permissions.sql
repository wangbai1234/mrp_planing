-- Keep profile edits separate from direct grants and credential resets.
INSERT INTO permission(code, name, type, parent_id, resource, action, sort)
SELECT 'user:assign_permission', '配置用户直授权限', 'BUTTON', id, 'user', 'assign_permission', 9
FROM permission WHERE code = 'user:list';

INSERT INTO permission(code, name, type, parent_id, resource, action, sort)
SELECT 'user:reset_password', '重置用户密码', 'BUTTON', id, 'user', 'reset_password', 10
FROM permission WHERE code = 'user:list';

INSERT IGNORE INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p
WHERE r.code = 'ADMIN' AND p.code IN ('user:assign_permission', 'user:reset_password');
