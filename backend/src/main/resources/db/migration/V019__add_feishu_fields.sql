-- V010: Add Feishu login fields to user table

ALTER TABLE `user` ADD COLUMN `feishu_user_id` VARCHAR(64) NULL UNIQUE COMMENT '飞书用户ID';
ALTER TABLE `user` ADD COLUMN `feishu_open_id` VARCHAR(64) NULL UNIQUE COMMENT '飞书Open ID';
ALTER TABLE `user` ADD COLUMN `avatar_url` VARCHAR(512) NULL COMMENT '头像URL';
ALTER TABLE `user` ADD COLUMN `auth_source` VARCHAR(32) DEFAULT 'LOCAL' COMMENT '认证来源: LOCAL/FEISHU';
ALTER TABLE `user` MODIFY COLUMN `password_hash` VARCHAR(255) NULL COMMENT '密码哈希(飞书用户可为空)';