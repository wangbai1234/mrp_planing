-- Independent factory maintenance permission; preserve existing grants when splitting the menu.
SET @config_menu = (SELECT id FROM permission WHERE code='mrp_config' LIMIT 1);
SET @warehouse_view = (SELECT id FROM permission WHERE code='mrp_warehouse_mapping:view' LIMIT 1);
SET @warehouse_manage = (SELECT id FROM permission WHERE code='mrp_warehouse_mapping:manage' LIMIT 1);

INSERT IGNORE INTO permission(code, name, type, parent_id, resource, action, sort) VALUES
('mrp_factory:view', '查看工厂管理', 'PAGE', @config_menu, 'mrp_factory', 'view', 3),
('mrp_factory:manage', '维护工厂管理', 'BUTTON', NULL, 'mrp_factory', 'manage', 1);

SET @factory_view = (SELECT id FROM permission WHERE code='mrp_factory:view' LIMIT 1);
SET @factory_manage = (SELECT id FROM permission WHERE code='mrp_factory:manage' LIMIT 1);
UPDATE permission SET parent_id=@factory_view WHERE id=@factory_manage;
UPDATE permission SET name='查看仓库管理' WHERE code='mrp_warehouse_mapping:view';
UPDATE permission SET name='维护仓库管理' WHERE code='mrp_warehouse_mapping:manage';

INSERT IGNORE INTO role_permission(role_id, permission_id)
SELECT role_id, @factory_view FROM role_permission WHERE permission_id=@warehouse_view;
INSERT IGNORE INTO role_permission(role_id, permission_id)
SELECT role_id, @factory_manage FROM role_permission WHERE permission_id=@warehouse_manage;
