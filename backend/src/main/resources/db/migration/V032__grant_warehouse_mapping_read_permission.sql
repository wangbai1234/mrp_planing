-- V032: Inventory imports obtain warehouse options from the published factory-warehouse mapping.
-- Warehouse users therefore need read access to that mapping; write access remains restricted.

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'mrp_warehouse_mapping:view'
WHERE r.code = 'WAREHOUSE';
