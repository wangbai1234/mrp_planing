-- C025: material control owns stock-risk disposition after the machine schedule is confirmed.
-- Keep the already-applied V058 migration immutable.
INSERT IGNORE INTO role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.code = 'mrp_inventory:allocate'
WHERE r.code = 'MATERIAL';
