-- C025: separate schedule and material ownership without changing historical permissions in place.
SET @workspace = (SELECT id FROM permission WHERE code='mrp_workspace' LIMIT 1);
INSERT IGNORE INTO permission(code,name,type,parent_id,resource,action,sort) VALUES
('mrp_shortage','物料缺料','MENU',@workspace,'mrp_shortage',NULL,2),
('mrp_schedule:confirm','确认整机排产','BUTTON',NULL,'mrp_schedule','confirm',1),
('mrp_shortage:manage','处理物料缺料','BUTTON',NULL,'mrp_shortage','manage',2),
('mrp_shortage:confirm','确认物料分析','BUTTON',NULL,'mrp_shortage','confirm',3);
SET @shortage = (SELECT id FROM permission WHERE code='mrp_shortage' LIMIT 1);
SET @schedule = (SELECT id FROM permission WHERE code='mrp_workspace:view' LIMIT 1);
INSERT IGNORE INTO permission(code,name,type,parent_id,resource,action,sort) VALUES
('mrp_shortage:view','查看物料缺料','PAGE',@shortage,'mrp_shortage','view',1);
UPDATE permission SET parent_id=@schedule WHERE code='mrp_schedule:confirm';
UPDATE permission SET parent_id=(SELECT id FROM (SELECT id FROM permission WHERE code='mrp_shortage:view' LIMIT 1) p)
WHERE code IN ('mrp_shortage:manage','mrp_shortage:confirm');

INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code IN
('mrp_shortage','mrp_shortage:view','mrp_shortage:manage','mrp_shortage:confirm')
WHERE r.code='ADMIN';
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code IN
('mrp_shortage','mrp_shortage:view','mrp_schedule:confirm')
WHERE r.code='PLANNER';
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code IN
('mrp_shortage','mrp_shortage:view','mrp_shortage:manage','mrp_shortage:confirm','mrp_decision:manage')
WHERE r.code='MATERIAL';
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code IN ('mrp_shortage','mrp_shortage:view')
WHERE r.code IN ('WAREHOUSE','MANAGER','READONLY');
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='mrp_schedule:confirm' WHERE r.code='ADMIN';

-- The legacy combined workspace granted material writes to planners. Move those actions to material control.
DELETE rp FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id
WHERE r.code='PLANNER' AND p.code IN ('mrp_decision:manage','mrp_inventory:allocate');
