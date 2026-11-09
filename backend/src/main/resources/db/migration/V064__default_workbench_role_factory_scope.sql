-- Workbench roles can see every factory by default; preserve any scope already configured for a role.
INSERT INTO role_data_scope (role_id, scope_type)
SELECT r.id, 'ALL'
FROM role r
WHERE r.code IN ('PLANNER', 'MATERIAL')
  AND NOT EXISTS (SELECT 1 FROM role_data_scope ds WHERE ds.role_id = r.id);
