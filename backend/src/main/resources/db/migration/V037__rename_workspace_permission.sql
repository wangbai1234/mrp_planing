-- The permission page is now laid out from the frontend menu declaration, so the
-- permission names shown there must match the pages. Codes and parent links are
-- unchanged: existing role assignments keep working.

UPDATE permission SET name = '排产计划' WHERE code = 'mrp_workspace';
UPDATE permission SET name = '查看排产计划' WHERE code = 'mrp_workspace:view';
UPDATE permission SET name = '计算配置' WHERE code = 'mrp_config';
